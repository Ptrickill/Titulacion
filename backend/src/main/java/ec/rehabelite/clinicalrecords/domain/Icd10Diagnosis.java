package ec.rehabelite.clinicalrecords.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** ICD-10 catalog entry (V2 migration); read-only for the application. Descriptions are in Spanish. */
@Entity
@Table(name = "icd10_diagnoses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Icd10Diagnosis {

    @Id
    @Column(length = 7)
    private String code;

    @Column(nullable = false, length = 255)
    private String description;

}
