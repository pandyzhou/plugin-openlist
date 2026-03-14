import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { resolve } from "path";

export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: resolve(__dirname, "../src/main/resources/console"),
    emptyOutDir: true,
    lib: {
      entry: resolve(__dirname, "src/index.ts"),
      name: "PluginOpenList",
      formats: ["iife"],
      fileName: () => "main.js",
    },
    rollupOptions: {
      external: [
        "vue",
        "vue-router",
        "@halo-dev/ui-shared",
        "@halo-dev/components",
        "@halo-dev/api-client",
        "axios",
      ],
      output: {
        globals: {
          vue: "Vue",
          "vue-router": "VueRouter",
          "@halo-dev/ui-shared": "HaloUiShared",
          "@halo-dev/components": "HaloComponents",
          "@halo-dev/api-client": "HaloApiClient",
          axios: "axios",
        },
      },
    },
  },
});
