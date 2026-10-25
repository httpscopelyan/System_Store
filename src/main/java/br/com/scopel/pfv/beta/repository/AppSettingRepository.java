package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {

    Optional<AppSetting> findByKey(String key);
}
