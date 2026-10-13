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

const chatPanel = ref();
const hotQuestions = [
  "零工中途跑了怎么办？",
  "临时有变化不需要招工了怎么办？",
  "工作地址填错了怎么办？",
  "招工信息需要修改怎么办？",
  "零工完工后没有点击收工，一直没法结算报酬怎么办？",
];

onShow(() => chatPanel.value?.resume());
onHide(() => chatPanel.value?.pause());
</script>
