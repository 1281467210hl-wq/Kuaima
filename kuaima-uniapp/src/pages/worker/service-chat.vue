<template>
  <ServiceChatPanel
    ref="chatPanel"
    :hot-questions="hotQuestions"
    fallback-path="/pages/worker/service"
    :initial-input="initialInput"
  />
</template>

<script setup>
import { onMounted, ref } from "vue";
import { onHide, onLoad, onShow } from "@dcloudio/uni-app";
import { getFaqs } from "@/api/chat";
import ServiceChatPanel from "@/components/ServiceChatPanel.vue";

const chatPanel = ref();
const initialInput = ref("");
const hotQuestions = ref([]);

onMounted(loadHotQuestions);

onShow(() => chatPanel.value?.resume());
onHide(() => chatPanel.value?.pause());
onLoad((options = {}) => {
  initialInput.value = decodeURIComponent(options.initialInput || "");
});

async function loadHotQuestions() {
  try {
    const result = await getFaqs();
    const rows = Array.isArray(result) ? result : result?.records || result?.content || [];
    hotQuestions.value = rows
      .map((item) => String(item.question || "").trim())
      .filter(Boolean);
  } catch (_) {
    hotQuestions.value = [];
  }
}
</script>
