import { definePlugin } from "@halo-dev/ui-shared";
import { markRaw } from "vue";
import SyncTab from "./views/SyncTab.vue";

export default definePlugin({
  extensionPoints: {
    "plugin:self:tabs:create": () => {
      return [
        {
          id: "openlist-sync",
          label: "文件同步",
          component: markRaw(SyncTab),
        },
      ];
    },
  },
});
