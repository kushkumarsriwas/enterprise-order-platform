# Deploying Nexora on AWS

Two ways, from simplest to most production-like. Both use the same images and configuration as the local stack.

| | A. One EC2 server + Docker Compose | B. Amazon EKS (Kubernetes) |
|---|---|---|
| What runs | the 12 containers of `docker-compose.yml` on one VM | the manifests of `k8s/` through the `deploy/aws` overlay |
| Images | built on the server | pulled from GitHub Container Registry (pushed by CI from `main`) |
| Public entry | ports 3000 and 3001 of the server | one AWS load balancer per web application |
| Scaling | vertical (a bigger instance) | `kubectl scale`, HorizontalPodAutoscaler, more nodes |
| Cost while running | one instance (a `t3.xlarge` is about USD 0.2 an hour) | EKS control plane USD 0.10 an hour + two nodes + load balancers |

Stop or delete what you created when the demo is over; AWS charges by the hour.

---

## A. One EC2 server with Docker Compose

1. **Launch an instance**: Amazon Linux 2023, `t3.xlarge` (4 vCPU, 16 GB; Oracle alone needs about 2 GB), 30 GB gp3
   disk. Security group: SSH (22) from your IP, and 3000 / 3001 from your IP (or from anywhere for a public demo).
   Region `me-central-1` (UAE) keeps it close to Dubai.
2. **Install Docker and Compose** (SSH in as `ec2-user`):
   ```bash
   sudo dnf install -y docker git
   sudo systemctl enable --now docker
   sudo usermod -aG docker ec2-user && newgrp docker
   sudo mkdir -p /usr/local/lib/docker/cli-plugins
   sudo curl -sSL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
        -o /usr/local/lib/docker/cli-plugins/docker-compose
   sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
   ```
3. **Configure and start**:
   ```bash
   git clone https://github.com/kushkumarsriwas/enterprise-order-platform.git && cd enterprise-order-platform
   cp .env.example .env
   PUBLIC_IP=$(curl -s https://checkip.amazonaws.com)
   sed -i "s|^ADMIN_PASSWORD=.*|ADMIN_PASSWORD=$(openssl rand -hex 12)|; s|^INTERNAL_API_KEY=.*|INTERNAL_API_KEY=$(openssl rand -hex 32)|" .env
   echo "MARKETPLACE_URL=http://$PUBLIC_IP:3000" >> .env
   docker compose up -d --build --wait          # 15-25 minutes the first time
   grep ADMIN_PASSWORD .env                     # the Control Center password
   ```
4. **Open** `http://<public-ip>:3000` (marketplace) and `http://<public-ip>:3001` (Control Center). The API gateway
   (8080) stays closed to the internet: the web applications reach it through their own `/api` proxy.
5. **Demo data** (optional): `docker run --rm --network enterprise-order-platform_default -e ADMIN_PASSWORD=... -v "$PWD/scripts:/scripts:ro" node:22-alpine node /scripts/demo-data.mjs http://api-gateway:8080`,
   then the dating steps of `scripts/windows/run-local.cmd`.

For a real deployment: an Elastic IP, a domain, HTTPS in front (an Application Load Balancer with an ACM certificate,
or Caddy on the instance), and backups of the Docker volumes (or the managed databases below).

---

## B. Amazon EKS with `deploy/aws`

`deploy/aws/kustomization.yaml` reuses every manifest of `k8s/` and changes only what differs on AWS: the images come
from `ghcr.io/kushkumarsriwas/eop-*`, the two web applications get public load balancers, the API gateway is
internal (`ClusterIP`), and the database volumes are encrypted EBS gp3 disks (`storageclass.yaml`).

### 1. Tools and images
* Install the AWS CLI, `eksctl` and `kubectl`, and run `aws configure` with an IAM user that may create clusters.
* CI pushes the eight images to GitHub Container Registry on every green build of `main`. Make them pullable by the
  cluster: GitHub → your profile → **Packages** → each `eop-*` package → **Package settings** → **Change visibility**
  → Public. (For private images, create a `docker-registry` Secret and add it as `imagePullSecrets`.)

### 2. Create the cluster (about 20 minutes)
```bash
eksctl create cluster --name nexora --region me-central-1 --nodes 2 --node-type t3.xlarge --with-oidc
```

### 3. Storage for the databases (EBS CSI driver)
```bash
eksctl create iamserviceaccount --name ebs-csi-controller-sa --namespace kube-system --cluster nexora \
  --region me-central-1 --role-name NexoraEbsCsiDriverRole --role-only \
  --attach-policy-arn arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy --approve
eksctl create addon --name aws-ebs-csi-driver --cluster nexora --region me-central-1 \
  --service-account-role-arn arn:aws:iam::<ACCOUNT_ID>:role/NexoraEbsCsiDriverRole --force
```

### 4. Secrets and deployment
```bash
cp k8s/config/secrets.env.example k8s/config/secrets.env    # git-ignored: put strong values in every line
kubectl apply -k deploy/aws
kubectl -n enterprise-platform get pods -w                  # Oracle takes 5-10 minutes the first time
kubectl -n enterprise-platform get service frontend admin-frontend
```
The `EXTERNAL-IP` column shows the two load balancer addresses: the marketplace and the Control Center. Point the
Control Center's marketplace links at the first one:
```bash
kubectl -n enterprise-platform patch configmap platform-config --type merge \
  -p '{"data":{"MARKETPLACE_URL":"http://<frontend-load-balancer>"}}'
kubectl -n enterprise-platform rollout restart deployment/admin-frontend
```

### 5. Show it working
```bash
kubectl -n enterprise-platform scale deployment order-service --replicas=3     # scale out
kubectl -n enterprise-platform delete pod -l app=order-service                 # self-healing
kubectl -n enterprise-platform rollout restart deployment/order-service        # zero-downtime rolling update
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl -n enterprise-platform get hpa                                         # CPU-based autoscaling (2-5 pods)
```

### 6. Continuous deployment from GitHub Actions
`.github/workflows/cd.yml` deploys every green build of `main` to the cluster, with the images pinned to the tested
commit. Create an IAM user for it (programmatic access only), let it into the cluster:
```bash
aws eks create-access-entry --cluster-name nexora --region me-central-1 \
  --principal-arn arn:aws:iam::<ACCOUNT_ID>:user/nexora-github
aws eks associate-access-policy --cluster-name nexora --region me-central-1 \
  --principal-arn arn:aws:iam::<ACCOUNT_ID>:user/nexora-github \
  --policy-arn arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy --access-scope type=cluster
```
and add these repository secrets (GitHub → Settings → Secrets and variables → Actions): `AWS_ACCESS_KEY_ID`,
`AWS_SECRET_ACCESS_KEY`, `AWS_REGION` (`me-central-1`), `EKS_CLUSTER_NAME` (`nexora`) and `K8S_SECRETS_ENV` (the
whole content of your `secrets.env`). Without them the EKS job is skipped.

### 7. Delete everything
```bash
kubectl delete namespace enterprise-platform        # removes the load balancers and the EBS volumes
eksctl delete cluster --name nexora --region me-central-1
```

### Going further in production
* Managed databases instead of database pods: **Amazon RDS** for PostgreSQL, MySQL and Oracle, **ElastiCache** for
  Redis. Only the JDBC URLs in `k8s/config/configmap.yaml` and the passwords change.
* **AWS Secrets Manager** with the External Secrets Operator instead of `secrets.env`.
* HTTPS with the **AWS Load Balancer Controller** (one ALB with Ingress rules) and an **ACM** certificate.
* **Amazon ECR** for private images, **CloudWatch Container Insights** for logs and metrics.
