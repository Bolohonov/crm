<template>
  <div class="info-field" v-if="value">
    <span class="info-field__label">{{ label }}</span>
    <span class="info-field__value" :class="{ 'font-mono': monospace }">{{ value }}</span>
  </div>
  <div class="info-field info-field--empty" v-else>
    <span class="info-field__label">{{ label }}</span>
    <span class="info-field__value info-field__value--empty">—</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label: string
  value?: string | null
  monospace?: boolean
}>()
</script>

<style scoped>
.info-field {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.info-field__label {
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.info-field__value {
  font-size: 0.9rem;
  color: var(--text-primary);
  line-height: 1.4;
}

.info-field__value--empty {
  color: var(--text-muted);
}
</style>
