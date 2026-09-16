<script setup lang="ts">
import { RouterView, useRouter, useRoute } from 'vue-router'
import { ElContainer, ElAside, ElMenu, ElMenuItem, ElMain, ElHeader } from 'element-plus'

const router = useRouter()
const route = useRoute()

const menuItems = [
  { path: '/appliance', label: '电器管理' },
  { path: '/room', label: '休息室管理' },
  { path: '/ship', label: '船舶管理' },
  { path: '/relation', label: '关联绑定' },
  { path: '/key', label: '钥匙领还' },
  { path: '/patrol', label: '夜班巡检交班' },
  { path: '/linen', label: '布草回收' },
  { path: '/fuel', label: '发电机加油' },
  { path: '/statistics', label: '统计查询' },
  { path: '/logs', label: '变更记录' }
]

const handleMenuClick = (path: string) => {
  router.push(path)
}
</script>

<template>
  <ElContainer style="height: 100vh;">
    <ElAside width="200px" style="background-color: #304156;">
      <div style="color: #fff; font-size: 18px; padding: 20px; text-align: center; border-bottom: 1px solid #435a70;">
        船舶码头船员休息室电器管理系统
      </div>
      <ElMenu
        mode="vertical"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        :default-active="route.path"
      >
        <ElMenuItem
          v-for="item in menuItems"
          :key="item.path"
          :index="item.path"
          @click="handleMenuClick(item.path)"
        >
          {{ item.label }}
        </ElMenuItem>
      </ElMenu>
    </ElAside>
    <ElContainer>
      <ElHeader style="background-color: #fff; border-bottom: 1px solid #e6e6e6;">
        <div style="font-size: 16px; font-weight: bold; color: #303133;">
          {{ menuItems.find(item => item.path === route.path)?.label }}
        </div>
      </ElHeader>
      <ElMain>
        <RouterView />
      </ElMain>
    </ElContainer>
  </ElContainer>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
}

#app {
  height: 100%;
}
</style>