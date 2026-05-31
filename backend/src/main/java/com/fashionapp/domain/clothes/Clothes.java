package com.fashionapp.domain.clothes;

import com.fashionapp.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clothes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Clothes {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String imageUrl;

    private String category;
    private String color;
    private String pattern;
    private String season;
    private String styleTag;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public void update(ClothesUpdateRequest request) {
        if (request.getCategory() != null) this.category = request.getCategory();
        if (request.getColor() != null)    this.color    = request.getColor();
        if (request.getPattern() != null)  this.pattern  = request.getPattern();
        if (request.getSeason() != null)   this.season   = request.getSeason();
        if (request.getStyleTag() != null) this.styleTag = request.getStyleTag();
    }
}
