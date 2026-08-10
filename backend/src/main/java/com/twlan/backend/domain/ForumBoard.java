package com.twlan.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "forum_board")
public class ForumBoard {

    // PUBLIC: every member; PRIVATE: members with the internal-forum privilege; HIDDEN: trusted members.
    public enum Kind { PUBLIC, PRIVATE, HIDDEN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tribeId;
    @Column(length = 60)
    private String name;
    @Enumerated(EnumType.STRING)
    private Kind kind = Kind.PUBLIC;
    private int position;

    public Long getId() { return id; }
    public Long getTribeId() { return tribeId; }
    public void setTribeId(Long tribeId) { this.tribeId = tribeId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
