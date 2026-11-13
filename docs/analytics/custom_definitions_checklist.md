# GA4 custom definitions to register

Admin > Data display > Custom definitions. Events must have been received once before a parameter can be picked.

## User-scoped dimensions (3 of 25)

- [ ] `user_cohort`
- [ ] `app_language`
- [ ] `onboarding_state`

## Event-scoped dimensions (13 of 50)

- [ ] `app_language`  (used by: sent on every event)
- [ ] `duration_bucket`  (used by: trip_save_success)
- [ ] `error_code`  (used by: ads_failed)
- [ ] `format`  (used by: ads_clicked, ads_failed, ads_loaded, ads_shown)
- [ ] `level`  (used by: limit_alert_fired)
- [ ] `limit_kmh`  (used by: speed_limit_changed)
- [ ] `placement`  (used by: ads_clicked, ads_failed, ads_loaded, ads_shown)
- [ ] `screen`  (used by: screen_enter)
- [ ] `step`  (used by: onboarding_skipped, onboarding_step_viewed)
- [ ] `style`  (used by: meter_style_changed)
- [ ] `tracking_version`  (used by: sent on every event)
- [ ] `user_cohort` **[required by an audience/funnel]**  (used by: limit_alert_fired, meter_style_changed, onboarding_skipped, speed_limit_changed, trip_discarded, trip_save_success, trip_started)
- [ ] `vehicle`  (used by: trip_started, vehicle_changed)

## Custom metrics (0 of 50)


Register the parameters the audiences filter on BEFORE building the audiences.
