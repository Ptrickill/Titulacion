package ec.rehabelite.shared.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * JPA converter of a clinical free-text column (MEJ-06): the entity holds the text, the column its AES-256-GCM form
 * ({@link ClinicalDataCipher}). It is applied field by field with {@code @Convert(converter = EncryptedText.class)},
 * never automatically, and Hibernate Envers uses it too, so the {@code _aud} tables are encrypted as well.
 * <p>
 * It is a Spring bean: Spring Boot hands Hibernate its bean container, so the converter receives the cipher. Only
 * equality and {@code IS NULL} work on these columns in a query: never filter, sort or search by them.
 */
@Component
@Converter
public class EncryptedText implements AttributeConverter<String, String> {

    private final ClinicalDataCipher cipher;

    public EncryptedText(ClinicalDataCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }

}
