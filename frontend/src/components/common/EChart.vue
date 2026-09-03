<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'

echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
const props = defineProps<{ option: EChartsCoreOption }>()
const root = ref<HTMLDivElement>()
let chart: ReturnType<typeof echarts.init> | null = null
const resize = () => chart?.resize()

onMounted(() => {
  chart = echarts.init(root.value!)
  chart.setOption(props.option)
  window.addEventListener('resize', resize)
})
watch(() => props.option, (value) => chart?.setOption(value, { notMerge: true }), { deep: true })
onBeforeUnmount(() => { window.removeEventListener('resize', resize); chart?.dispose() })
</script>

<template><div ref="root" style="width: 100%; height: 300px" /></template>
