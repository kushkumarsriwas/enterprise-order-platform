package com.enterprise.payment.security;

import java.security.Key;

/** Resolves the public key that verifies a token signed with the given key id ("kid"). */
public interface KeyResolver {

    Key resolve(String keyId);
}
