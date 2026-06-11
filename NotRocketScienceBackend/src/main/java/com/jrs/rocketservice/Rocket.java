package com.jrs.rocketservice;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "rockets")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "agency")
public class Rocket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private SpaceAgency agency;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "height", precision = 8, scale = 2)
    private BigDecimal height;

    @Column(name = "diameter", precision = 8, scale = 2)
    private BigDecimal diameter;

    @Column(name = "mass", precision = 12, scale = 2)
    private BigDecimal mass;

    @Column(name = "payload_to_leo", precision = 10, scale = 2)
    private BigDecimal payloadToLeo;

    @Column(name = "thrust_kn")
    private Double thrustKn;

    @Column(name = "reusable")
    private Boolean reusable;

    @Column(name = "human_crew_capacity")
    private Integer humanCrewCapacity;

    @Column(name = "number_of_stages")
    private Integer numberOfStages;

    @Column(name = "first_launch_date")
    private LocalDate firstLaunchDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Rocket(SpaceAgency agency, String name, String status) {
        this.agency = agency;
        this.name = name;
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rocket other)) return false;
        return name != null && name.equals(other.name)
                && agency != null && agency.equals(other.agency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, agency);
    }
}
