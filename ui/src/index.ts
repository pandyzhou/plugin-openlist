import { definePlugin } from "@halo-dev/ui-shared";
import BlankLayout from "./layouts/BlankLayout.vue";
import SyncPage from "./views/SyncPage.vue";

export default definePlugin({
  routes: [
    {
      path: "/openlist",
      name: "OpenListRoot",
      component: BlankLayout,
      meta: {
        title: "OpenList 同步",
        searchable: true,
        menu: {
          name: "OpenList 同步",
          group: "tool",
          priority: 0,
        },
      },
      children: [
        {
          path: "",
          name: "OpenListSync",
          component: SyncPage,
        },
      ],
    },
  ],
});
