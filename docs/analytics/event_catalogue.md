# Event catalogue: Speedometer

Every event also carries `tracking_version`, `app_language`, `user_cohort` (added by JourneyTracker).

| Event | Parameters | Trigger |
|---|---|---|
| `screen_enter` | `screen` | every screen change in AppNavHost |
| `app_foregrounded` | - | app comes to the front (ProcessLifecycleOwner) |
| `app_backgrounded` | - | app goes to the background (ProcessLifecycleOwner) |
| `onboarding_started` | - | first intro page shown |
| `onboarding_step_viewed` | `step` | each intro page (1 to 5) |
| `onboarding_skipped` | `step` | Skip pressed before the last page |
| `onboarding_completed` | - | Finish setup on the vehicle screen |
| `trip_started` | `vehicle` | trip goes from idle to recording |
| `trip_paused` | - | recording trip paused |
| `trip_resumed` | - | paused trip resumed |
| `trip_save_success` | `duration_bucket` | Save pressed on Results |
| `trip_discarded` | - | Discard confirmed on Results |
| `speed_limit_changed` | `limit_kmh` | speed limit saved in Settings |
| `limit_alert_fired` | `level` | speed goes over the limit (once per time over) |
| `meter_style_changed` | `style` | gauge style saved |
| `vehicle_changed` | `vehicle` | vehicle picked |
| `ads_loaded` | `format`, `placement` | an ad finished loading |
| `ads_failed` | `format`, `placement`, `error_code` | an ad failed to load |
| `ads_shown` | `format`, `placement` | an ad was shown |
| `ads_clicked` | `format`, `placement` | an ad was clicked |
