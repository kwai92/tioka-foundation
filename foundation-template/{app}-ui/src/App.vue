<template>
  <el-container>
    <el-header>{app}</el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
// 占位布局：在门户内运行时由前端包隐藏外层布局（S15 §6.3）。
</script>
