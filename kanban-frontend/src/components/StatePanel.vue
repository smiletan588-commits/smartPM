<template>
  <div class="state-panel" :class="`is-${tone}`" :role="tone === 'error' ? 'alert' : 'status'" :aria-live="tone === 'error' ? 'assertive' : 'polite'"><div class="state-mark" aria-hidden="true"><slot name="icon">{{ tone === 'error' ? '!' : '+' }}</slot></div><h3>{{ title }}</h3><p v-if="description">{{ description }}</p><div v-if="$slots.actions" class="state-actions"><slot name="actions" /></div></div>
</template>
<script setup>
defineProps({ title: { type: String, required: true }, description: { type: String, default: '' }, tone: { type: String, default: 'empty' } })
</script>
<style scoped>
.state-panel { display: grid; justify-items: start; max-width: 520px; padding: 42px 6px; }
.state-mark { display: grid; place-items: center; width: 42px; height: 42px; margin-bottom: 20px; border: 1px solid var(--border); border-radius: 12px; color: var(--brand); background: var(--brand-soft); font-size: 22px; font-weight: 500; }
.is-error .state-mark { color: var(--danger); background: var(--danger-bg); }
h3 { margin: 0; font-size: 20px; letter-spacing: -.02em; }
p { max-width: 44ch; margin: 8px 0 0; color: var(--text-tertiary); }
.state-actions { display: flex; gap: 10px; margin-top: 20px; }
</style>
