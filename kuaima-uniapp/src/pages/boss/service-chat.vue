<template>
  <ServiceChatPanel
    ref="chatPanel"
    :hot-questions="hotQuestions"
    fallback-path="/pages/boss/profile"
  />
</template>

<script setup>
import { ref } from "vue";
import { onHide, onShow } from "@dcloudio/uni-app";
import ServiceChatPanel from "@/components/ServiceChatPanel.vue";
import { useServiceFaqs } from "@/composables/useServiceFaqs";

const chatPanel = ref();
const { hotQuestions, loadServiceFaqs } = useServiceFaqs();

onShow(() => {
  loadServiceFaqs();
  chatPanel.value?.resume();
});
onHide(() => chatPanel.value?.pause());
</script>
