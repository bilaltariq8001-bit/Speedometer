# User properties: Speedometer

| Property | Values | Meaning | Set by |
|---|---|---|---|
| `user_cohort` | `first_time`, `returning` | first_time until the first trip is saved, returning afterwards | Analytics.refreshUserProperties (AnalyticsStateStore) |
| `app_language` | free text <= 36 chars | language code of the phone or app | Analytics.refreshUserProperties |
| `onboarding_state` | `not_started`, `in_progress`, `completed` | onboarding progress | Analytics.onboardingStep / onboardingCompleted |
