export default defineNuxtConfig({
  ssr: false,
  devtools: { enabled: false },
  compatibilityDate: "2026-09-06",
  css: ["~/assets/main.css"],
  app: {
    head: {
      title: "选课系统",
      htmlAttrs: { lang: "zh-CN" },
      meta: [{ name: "description", content: "按学期管理课程与选课安排" }],
    },
  },
  routeRules: {
    "/api/**": {
      proxy: `${process.env.API_TARGET || "http://127.0.0.1:8081"}/api/**`,
    },
  },
});
