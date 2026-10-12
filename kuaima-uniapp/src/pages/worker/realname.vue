<template>
  <RealnameFaceVerify
    role="worker"
    entry-title="完成实名后开启接单"
    success-desc="现在可以正常浏览岗位并接单"
    verified-desc="您的账户已完成实名认证"
    verified-mode="reverify"
    home-path="/pages/worker/home"
  />
</template>

<script setup>
import RealnameFaceVerify from "@/components/RealnameFaceVerify.vue";
</script>
