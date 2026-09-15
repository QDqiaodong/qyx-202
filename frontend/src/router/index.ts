import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/appliance'
    },
    {
      path: '/appliance',
      name: 'appliance',
      component: () => import('../views/ApplianceView.vue')
    },
    {
      path: '/room',
      name: 'room',
      component: () => import('../views/RoomView.vue')
    },
    {
      path: '/ship',
      name: 'ship',
      component: () => import('../views/ShipView.vue')
    },
    {
      path: '/relation',
      name: 'relation',
      component: () => import('../views/RelationView.vue')
    },
    {
      path: '/key',
      name: 'key',
      component: () => import('../views/KeyView.vue')
    },
    {
      path: '/statistics',
      name: 'statistics',
      component: () => import('../views/StatisticsView.vue')
    },
    {
      path: '/logs',
      name: 'logs',
      component: () => import('../views/LogsView.vue')
    }
  ]
})

export default router