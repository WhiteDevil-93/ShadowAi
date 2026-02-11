package com.shadowai.hotswapping

import com.shadowai.core.ProviderId

/**
 * Validates provider configurations.
 */
class ProviderConfigValidator {
    /**
     * Validates a snapshot and returns any issues.
     */
    fun validate(snapshot: ProviderConfigSnapshot): List<ProviderConfigValidationIssue> {
        val issues = mutableListOf<ProviderConfigValidationIssue>()
        val providerIds = snapshot.providers.map { it.providerId }
        val duplicates = providerIds.groupingBy { it }.eachCount().filter { it.value > 1 }

        duplicates.keys.forEach { duplicateId ->
            issues.add(
                ProviderConfigValidationIssue(
                    providerId = duplicateId,
                    code = "DUPLICATE_PROVIDER",
                    message = "Provider ${duplicateId.name} appears multiple times."
                )
            )
        }

        snapshot.providers.forEach { config ->
            issues.addAll(validateConfig(config))
        }

        return issues
    }

    /**
     * Validates a single provider configuration.
     */
    fun validateConfig(config: ProviderConfig): List<ProviderConfigValidationIssue> {
        val issues = mutableListOf<ProviderConfigValidationIssue>()

        if (config.providerId == ProviderId.UNKNOWN) {
            issues.add(
                ProviderConfigValidationIssue(
                    providerId = config.providerId,
                    code = "UNKNOWN_PROVIDER",
                    message = "ProviderId is UNKNOWN."
                )
            )
        }

        if (config.name.isBlank()) {
            issues.add(
                ProviderConfigValidationIssue(
                    providerId = config.providerId,
                    code = "NAME_REQUIRED",
                    message = "Provider name is required."
                )
            )
        }

        if (!config.isLocal && config.baseUrl.isBlank()) {
            issues.add(
                ProviderConfigValidationIssue(
                    providerId = config.providerId,
                    code = "BASE_URL_REQUIRED",
                    message = "Base URL is required for remote providers."
                )
            )
        }

        if (config.isLocal && config.baseUrl.isNotBlank()) {
            issues.add(
                ProviderConfigValidationIssue(
                    providerId = config.providerId,
                    code = "BASE_URL_UNEXPECTED",
                    message = "Local providers should not specify a base URL."
                )
            )
        }

        return issues
    }
}
