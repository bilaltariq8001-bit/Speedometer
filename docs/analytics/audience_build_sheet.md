# Audience build sheet: Speedometer

Admin > Data display > Audiences > New audience > Create a custom audience. User-property conditions: **At the most recent interaction**. Parameters of one event go inside the SAME event condition. Audiences are prospective.

# First-time users

## Onboarding

### 1. First-time: Onboarding In Progress

**Goal:** new users who started the intro but have not finished setup

- [ ] User property `user_cohort` exactly matches `first_time` *(cohort condition, added automatically)*
- [ ] User property `onboarding_state` exactly matches `in_progress`
- [ ] Membership duration: 7 days
- [ ] Name: `First-time: Onboarding In Progress`

### 2. First-time: Skipped Onboarding

**Goal:** new users who skipped the intro and may not understand the app yet

- [ ] Event `onboarding_skipped`
    - [ ] parameter `user_cohort` exactly matches `first_time` *(cohort, added automatically)*
- [ ] Membership duration: 7 days
- [ ] Name: `First-time: Skipped Onboarding`

## Trips

### 3. First-time: Started a Trip

**Goal:** new users who started a trip but have not saved one yet

- [ ] Event `trip_started`
    - [ ] parameter `user_cohort` exactly matches `first_time` *(cohort, added automatically)*
- [ ] Membership duration: 7 days
- [ ] Name: `First-time: Started a Trip`

## Speed limit alert

### 4. First-time: Set a Speed Limit

**Goal:** new users who discovered the speed limit alert

- [ ] Event `speed_limit_changed`
    - [ ] parameter `user_cohort` exactly matches `first_time` *(cohort, added automatically)*
- [ ] Membership duration: 7 days
- [ ] Name: `First-time: Set a Speed Limit`

# Returning users

## Trips

### 5. Returning: Saved Trips

**Goal:** users who saved at least one trip: the active core users

- [ ] Event `trip_save_success`
    - [ ] parameter `user_cohort` exactly matches `returning` *(cohort, added automatically)*
- [ ] Membership duration: 30 days
- [ ] Name: `Returning: Saved Trips`

### 6. Returning: Discarded Trips

**Goal:** users who threw trips away, a sign of GPS or recording problems

- [ ] Event `trip_discarded`
    - [ ] parameter `user_cohort` exactly matches `returning` *(cohort, added automatically)*
- [ ] Membership duration: 30 days
- [ ] Name: `Returning: Discarded Trips`

## Speed limit alert

### 7. Returning: Limit Alert Fired

**Goal:** users who actually went over their limit

- [ ] Event `limit_alert_fired`
    - [ ] parameter `user_cohort` exactly matches `returning` *(cohort, added automatically)*
- [ ] Membership duration: 30 days
- [ ] Name: `Returning: Limit Alert Fired`

## Meter and vehicle

### 8. Returning: Changed Meter Style

**Goal:** users who personalise the app

- [ ] Event `meter_style_changed`
    - [ ] parameter `user_cohort` exactly matches `returning` *(cohort, added automatically)*
- [ ] Membership duration: 30 days
- [ ] Name: `Returning: Changed Meter Style`

# All users (cross-cutting)

## Ads

### 9. All: Ad Clicked

**Goal:** users who click ads, to compare ad placements

- [ ] Event `ads_clicked`
- [ ] Membership duration: 30 days
- [ ] Name: `All: Ad Clicked`

### 10. All: Ad Load Failures

**Goal:** users whose ads fail to load, to find fill or network problems

- [ ] Event `ads_failed`
- [ ] Membership duration: 7 days
- [ ] Name: `All: Ad Load Failures`

# Exploration funnels

Explore > Funnel exploration; indirectly followed steps. **Break down by `user_cohort`** to compare first-time and returning users.

## Onboarding (window 30 min)

1. `onboarding_started`
2. `onboarding_completed`

## First trip (window 1440 min)

1. `onboarding_completed`
2. `trip_started`
3. `trip_save_success`

