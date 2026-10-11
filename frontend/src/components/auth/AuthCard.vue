<script setup lang="ts">
defineProps<{
  title: string
  subtitle: string
}>()
</script>

<template>
  <main class="auth-page">
    <section class="auth-card">
      <RouterLink class="brand" to="/login">
        <span>CP</span>
        CampusPass
      </RouterLink>
      <h1>{{ title }}</h1>
      <p class="subtitle">{{ subtitle }}</p>
      <slot />
      <div class="footer"><slot name="footer" /></div>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 40px 20px;
  background:
    radial-gradient(circle at 12% 12%, rgb(91 124 231 / 18%), transparent 28%),
    #f5f7fb;
}

.auth-card {
  width: min(500px, 100%);
  padding: 46px;
  border: 1px solid #e3e8f1;
  border-radius: 24px;
  background: #ffffff;
  box-shadow: 0 24px 70px rgb(36 58 104 / 10%);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 38px;
  color: #17213a;
  font-size: 19px;
  font-weight: 700;
  text-decoration: none;
}

.brand span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  color: #ffffff;
  font-weight: 800;
  border-radius: 11px;
  background: #315bd9;
}

h1 {
  color: #17213a;
  font-size: 30px;
  font-weight: 700;
}

.subtitle {
  margin: 10px 0 30px;
  color: #7a8499;
}

.footer {
  margin-top: 24px;
  color: #7a8499;
  text-align: center;
}

@media (max-width: 560px) {
  .auth-card {
    padding: 32px 24px;
  }
}
</style>
