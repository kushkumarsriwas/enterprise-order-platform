package com.enterprise.notification.announcement;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    @Query("""
            select a from Announcement a
            where a.active = true and a.audience in :audiences and a.startsAt <= :now
              and (a.endsAt is null or a.endsAt > :now)
            order by a.startsAt desc""")
    List<Announcement> findLive(Collection<AnnouncementAudience> audiences, Instant now);

    List<Announcement> findAllByOrderByCreatedAtDesc();
}
