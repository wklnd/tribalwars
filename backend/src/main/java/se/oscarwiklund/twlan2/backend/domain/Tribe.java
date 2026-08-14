package se.oscarwiklund.twlan2.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

// Points and ranks are never stored: they are worked out from the members' villages.
@Entity
@Table(name = "tribe", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"worldId", "tagLower"}),
        @UniqueConstraint(columnNames = {"worldId", "nameLower"})})
public class Tribe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long worldId;

    @Column(length = 60)
    private String name;
    @Column(length = 60)
    private String nameLower;
    @Column(length = 6)
    private String tag;
    @Column(length = 6)
    private String tagLower;

    // BB code text shown on the tribe's profile.
    @Column(length = 4000)
    private String description;
    // BB code text shown to the members on the overview page.
    @Column(length = 4000)
    private String announcement;
    @Column(length = 128)
    private String homepage;
    @Column(length = 128)
    private String irc;
    private boolean allowApply;
    @Column(length = 2000)
    private String applyTemplate;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getWorldId() { return worldId; }
    public void setWorldId(Long worldId) { this.worldId = worldId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.nameLower = name == null ? null : name.toLowerCase(); }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; this.tagLower = tag == null ? null : tag.toLowerCase(); }
    public String getDescription() { return description == null ? "" : description; }
    public void setDescription(String description) { this.description = description; }
    public String getAnnouncement() { return announcement == null ? "" : announcement; }
    public void setAnnouncement(String announcement) { this.announcement = announcement; }
    public String getHomepage() { return homepage == null ? "" : homepage; }
    public void setHomepage(String homepage) { this.homepage = homepage; }
    public String getIrc() { return irc == null ? "" : irc; }
    public void setIrc(String irc) { this.irc = irc; }
    public boolean isAllowApply() { return allowApply; }
    public void setAllowApply(boolean allowApply) { this.allowApply = allowApply; }
    public String getApplyTemplate() { return applyTemplate == null ? "" : applyTemplate; }
    public void setApplyTemplate(String applyTemplate) { this.applyTemplate = applyTemplate; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
