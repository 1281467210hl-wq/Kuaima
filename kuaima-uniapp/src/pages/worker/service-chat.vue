<template>
  <ServiceChatPanel
    ref="chatPanel"
    :hot-questions="hotQuestions"
    fallback-path="/pages/worker/service"
    :initial-input="initialInput"
  />
</template>

<script setup>
import { ref } from "vue";
import { onHide, onLoad, onShow } from "@dcloudio/uni-app";
import ServiceChatPanel from "@/components/ServiceChatPanel.vue";
import { useServiceFaqs } from "@/composables/useServiceFaqs";

const chatPanel = ref();
const initialInput = ref("");
const { hotQuestions, loadServiceFaqs } = useServiceFaqs();

onShow(() => {
  loadServiceFaqs();
  chatPanel.value?.resume();
});
onHide(() => chatPanel.value?.pause());
onLoad((options = {}) => {
  initialInput.value = decodeURIComponent(options.initialInput || "");
});

</script>
