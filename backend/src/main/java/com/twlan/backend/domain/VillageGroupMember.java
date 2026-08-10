package com.twlan.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "village_group_member", uniqueConstraints = @UniqueConstraint(columnNames = {"groupId", "villageId"}))
public class VillageGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long groupId;
    private Long villageId;

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getVillageId() { return villageId; }
    public void setVillageId(Long villageId) { this.villageId = villageId; }
}
