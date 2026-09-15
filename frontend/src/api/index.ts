import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

export interface ElectricAppliance {
  id?: number
  deviceCode: string
  deviceName: string
  power: number
  applianceType: string
  status?: string
  roomId?: number
  roomCode?: string
  roomName?: string
  shipId?: number
  shipCode?: string
  shipName?: string
  lastChangeBatch?: string
}

export interface LoungeRoom {
  id?: number
  roomCode: string
  roomName: string
  floor?: string
  capacity?: number
  status?: string
  shipId?: number
  shipCode?: string
  shipName?: string
  changeBatch?: string
}

export interface Ship {
  id?: number
  shipCode: string
  shipName: string
  shipType?: string
  dockCode?: string
  status?: string
}

export interface RelationBind {
  deviceId?: number
  deviceCode?: string
  roomId?: number
  roomCode?: string
  shipId?: number
  shipCode?: string
  operator?: string
  remark?: string
}

export interface RelationChangeLog {
  id?: number
  changeType: string
  changeBatch?: string
  deviceId?: number
  deviceCode?: string
  roomId?: number
  roomCode?: string
  shipId?: number
  shipCode?: string
  oldRoomId?: number
  oldRoomCode?: string
  oldShipId?: number
  oldShipCode?: string
  operator?: string
  remark?: string
  changeTime?: string
}

export interface ShiftBlockedAppliance {
  id: number
  deviceCode: string
  deviceName: string
  status?: string
  roomId?: number
  roomCode?: string
  roomName?: string
  shipId?: number
  shipCode?: string
  shipName?: string
}

export interface ShiftResult {
  changeBatch: string
  roomCount: number
  applianceCount: number
  blockedAppliances?: ShiftBlockedAppliance[]
}

export const applianceApi = {
  list: () => request.get('/appliance'),
  get: (id: number) => request.get(`/appliance/${id}`),
  create: (data: ElectricAppliance) => request.post('/appliance', data),
  update: (id: number, data: ElectricAppliance) => request.put(`/appliance/${id}`, data),
  delete: (id: number) => request.delete(`/appliance/${id}`),
  listByRoom: (roomId: number) => request.get(`/appliance/room/${roomId}`),
  listByShip: (shipId: number) => request.get(`/appliance/ship/${shipId}`)
}

export const roomApi = {
  list: () => request.get('/room'),
  get: (id: number) => request.get(`/room/${id}`),
  create: (data: LoungeRoom) => request.post('/room', data),
  update: (id: number, data: LoungeRoom) => request.put(`/room/${id}`, data),
  delete: (id: number) => request.delete(`/room/${id}`),
  getWithShip: (id: number) => request.get(`/relation/room/${id}/with-ship`)
}

export const shipApi = {
  list: () => request.get('/ship'),
  get: (id: number) => request.get(`/ship/${id}`),
  create: (data: Ship) => request.post('/ship', data),
  update: (id: number, data: Ship) => request.put(`/ship/${id}`, data),
  delete: (id: number) => request.delete(`/ship/${id}`)
}

export const relationApi = {
  bind: (data: RelationBind) => request.post('/relation/bind', data),
  updateRelation: (roomId: number, shipId: number, operator?: string, remark?: string) =>
    request.put(`/relation/room/${roomId}/ship/${shipId}`, null, { params: { operator, remark } }),
  shipChange: (oldShipId: number, newShipId: number, operator?: string, remark?: string) =>
    request.put('/relation/ship/change', null, { params: { oldShipId, newShipId, operator, remark } }),
  getAppliancesByShip: (shipId: number) => request.get(`/relation/ship/${shipId}/appliances`),
  getAppliancesByRoom: (roomId: number) => request.get(`/relation/room/${roomId}/appliances`),
  getLogs: (deviceId?: number, roomId?: number, shipId?: number) =>
    request.get('/relation/logs', { params: { deviceId, roomId, shipId } })
}