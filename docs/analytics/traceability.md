# Traceability: audience -> what the app must send

| Cohort | Flow | Audience | Condition | Produced by |
|---|---|---|---|---|
| first_time | Onboarding | First-time: Onboarding In Progress | `user_cohort` = `first_time` | Analytics.refreshUserProperties (AnalyticsStateStore) |
| first_time | Onboarding | First-time: Onboarding In Progress | `onboarding_state` = `in_progress` | Analytics.onboardingStep / onboardingCompleted |
| first_time | Onboarding | First-time: Skipped Onboarding | event `onboarding_skipped` `user_cohort = first_time` | Skip pressed before the last page |
| first_time | Trips | First-time: Started a Trip | event `trip_started` `user_cohort = first_time` | trip goes from idle to recording |
| first_time | Speed limit alert | First-time: Set a Speed Limit | event `speed_limit_changed` `user_cohort = first_time` | speed limit saved in Settings |
| returning | Trips | Returning: Saved Trips | event `trip_save_success` `user_cohort = returning` | Save pressed on Results |
| returning | Trips | Returning: Discarded Trips | event `trip_discarded` `user_cohort = returning` | Discard confirmed on Results |
| returning | Speed limit alert | Returning: Limit Alert Fired | event `limit_alert_fired` `user_cohort = returning` | speed goes over the limit (once per time over) |
| returning | Meter and vehicle | Returning: Changed Meter Style | event `meter_style_changed` `user_cohort = returning` | gauge style saved |
| all | Ads | All: Ad Clicked | event `ads_clicked`  | an ad was clicked |
| all | Ads | All: Ad Load Failures | event `ads_failed`  | an ad failed to load |
