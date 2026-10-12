<template>
  <RealnameFaceVerify
    role="boss"
    entry-title="完成实名后开启招工"
    success-desc="现在可以正常发布招工需求"
    verified-desc="您的账户已完成实名认证，无需重复认证"
    verified-mode="back"
    home-path="/pages/boss/home"
  />
</template>

<script setup>
import RealnameFaceVerify from "@/components/RealnameFaceVerify.vue";
</script>
