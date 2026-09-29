<template>
  <span class="brand-lockup" :class="{ compact }" role="img" aria-label="SmartPM">
    <img class="brand-symbol" :src="iconUrl" alt="" aria-hidden="true" width="28" height="30" />
    <strong v-if="!compact" class="brand-name" aria-hidden="true">SmartPM</strong>
  </span>
</template>

<script setup>
defineProps({ compact: { type: Boolean, default: false } })
const iconUrl = `${import.meta.env.BASE_URL}smartpm-icon.svg`
</script>

<style scoped>
.brand-lockup { display: inline-flex; align-items: center; gap: 9px; min-width: 0; flex-shrink: 0; }
.brand-symbol { display: block; width: 28px; height: auto; flex: 0 0 auto; }
.brand-name { color: #172238; font-family: "Segoe UI", Arial, sans-serif; font-size: 19px; font-weight: 600; line-height: 1; letter-spacing: -.033em; white-space: nowrap; }
.compact .brand-symbol { width: 26px; height: auto; }
</style>
