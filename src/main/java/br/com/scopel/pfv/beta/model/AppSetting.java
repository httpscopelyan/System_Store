package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuração do sistema em chave/valor (PDV Prático: tela ConfigADM).
 * O valor é guardado como texto; leia com tipagem via AppSettingService.
 */
@Entity
@Table(name = "app_setting")
@Getter
@Setter
public class AppSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 60)
    private String key;

    @Column(name = "setting_value", length = 255)
    private String value;
}
