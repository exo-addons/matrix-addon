<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 3 of the License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
-->
<template>
  <div>
    <v-list-item dense class="mt-3">
      <v-list-item-content>
        <v-list-item-title class="text-wrap">
          {{ $t('UserSettings.pwa.chatNotifications') }}
        </v-list-item-title>
      </v-list-item-content>
      <v-list-item-action class="mt-0 mb-auto">
        <v-switch
          v-if="available"
          v-model="enabled"
          :loading="saving"
          :aria-label="$t('UserSettings.pwa.chatNotifications')"
          class="py-2 px-3"
          @change="save" />
        <v-tooltip v-else bottom>
          <template #activator="{ on, attrs }">
            <div
              v-bind="attrs"
              v-on="on">
              <v-switch disabled />
            </div>
          </template>
          <span v-if="!pwaSupported">
            {{ $t('UserSettings.pwa.browserNotSupported') }}
          </span>
          <span v-else-if="!pwaEnabled">
            {{ $t('UserSettings.pwa.pwaNotEnabled') }}
          </span>
          <span v-else-if="!installed">
            {{ $t('UserSettings.pwa.notification.pwaNotInstalled') }}
          </span>
          <span v-else-if="loadFailed">
            {{ $t('UserSettings.pwa.chatNotifications.loadError') }}
          </span>
          <span v-else>
            {{ $t('UserSettings.pwa.chatNotifications.notificationsNotAllowed') }}
          </span>
        </v-tooltip>
      </v-list-item-action>
    </v-list-item>
    <v-list-item
      v-if="available && enabled && lastSaved"
      dense>
      <v-list-item-content>
        <v-list-item-title class="text-wrap">
          {{ $t('UserSettings.pwa.chatNotifications.unreadDelay') }}
        </v-list-item-title>
      </v-list-item-content>
      <v-list-item-action class="mt-0 mb-auto">
        <div class="d-flex flex-row align-center">
          <number-input
            v-model="delayMinutes"
            :label="$t('UserSettings.pwa.chatNotifications.unreadDelay')"
            :min="1"
            :max="1440"
            :step="1"
            editable
            @input="scheduleDelaySave" />
          <span class="ps-3">{{ $t('UserSettings.pwa.chatNotifications.minutes') }}</span>
        </div>
      </v-list-item-action>
    </v-list-item>
  </div>
</template>
<script>
export default {
  props: {
    isMobile: {
      type: Boolean,
      default: false,
    },
    pwaEnabled: {
      type: Boolean,
      default: false,
    },
    pwaSupported: {
      type: Boolean,
      default: false,
    },
    installed: {
      type: Boolean,
      default: false,
    },
    notificationPermission: {
      type: String,
      default: null,
    },
    subscriptionId: {
      type: [String, Number],
      default: null,
    },
  },
  data: () => ({
    enabled: true,
    delayMinutes: 5,
    saving: false,
    initialized: false,
    subscribed: true,
    retried: false,
    loadFailed: false,
    lastSaved: null,
    retryTimeout: null,
    delaySaveTimeout: null,
  }),
  computed: {
    effectiveSubscriptionId() {
      return this.subscriptionId
        || window?.pwa?.getSubscriptionId?.()
        || null;
    },
    available() {
      // the local id always exists once generated: the server-side
      // subscription row is what makes the device really reachable
      return this.pwaEnabled
        && this.pwaSupported
        && this.installed
        && this.notificationPermission === 'granted'
        && !!this.effectiveSubscriptionId
        && this.subscribed
        && !this.loadFailed;
    },
  },
  watch: {
    available() {
      this.init();
    },
  },
  created() {
    this.init();
  },
  beforeDestroy() {
    if (this.retryTimeout) {
      window.clearTimeout(this.retryTimeout);
    }
    if (this.delaySaveTimeout) {
      window.clearTimeout(this.delaySaveTimeout);
    }
  },
  methods: {
    init() {
      if (!this.available || this.initialized) {
        return;
      }
      this.initialized = true;
      this.saving = true;
      this.$chatPwaSettingsService.getChatNotificationSetting(this.effectiveSubscriptionId)
        .then(setting => {
          this.subscribed = true;
          if (setting) {
            this.enabled = setting.enabled;
            this.delayMinutes = setting.delayMinutes || 5;
          }
          this.lastSaved = {
            enabled: this.enabled,
            delayMinutes: this.delayMinutes,
          };
        })
        .catch(error => {
          this.initialized = false;
          if (error?.notFound) {
            // no server-side subscription yet: right after a permission
            // grant its creation is in flight — retry once, else stay
            // disabled rather than exposing a toggle whose saves 404
            this.subscribed = false;
            if (!this.retried) {
              this.retried = true;
              this.retryTimeout = window.setTimeout(() => {
                this.subscribed = true;
                this.init();
              }, 5000);
            }
          } else {
            // the stored state is unknown: keep the toggle unavailable
            // rather than showing defaults that may not match it
            this.loadFailed = true;
            this.retryTimeout = window.setTimeout(() => {
              this.loadFailed = false;
              this.init();
            }, 10000);
          }
        })
        .finally(() => this.saving = false);
    },
    scheduleDelaySave() {
      if (!this.lastSaved) {
        // the stepper emits input while mounting: before the stored value has
        // been loaded, a save here would overwrite it with the default
        return;
      }
      if (this.delaySaveTimeout) {
        window.clearTimeout(this.delaySaveTimeout);
      }
      // save once the clicks settle, and only when the value really changed
      this.delaySaveTimeout = window.setTimeout(() => {
        if (this.lastSaved && this.lastSaved.delayMinutes !== this.delayMinutes) {
          this.save();
        }
      }, 800);
    },
    save() {
      this.saving = true;
      this.$chatPwaSettingsService.saveChatNotificationSetting(this.effectiveSubscriptionId, this.enabled, this.delayMinutes)
        .then(() => {
          this.lastSaved = {
            enabled: this.enabled,
            delayMinutes: this.delayMinutes,
          };
          this.$root.$emit('alert-message', this.$t('UserSettings.pwa.chatNotifications.savedSuccessfully'), 'success');
        })
        .catch(() => {
          if (this.lastSaved) {
            this.enabled = this.lastSaved.enabled;
            this.delayMinutes = this.lastSaved.delayMinutes;
          }
          this.$root.$emit('alert-message', this.$t('UserSettings.pwa.chatNotifications.saveError'), 'error');
        })
        .finally(() => this.saving = false);
    },
  },
};
</script>
