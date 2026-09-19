#!/usr/bin/env node
/**
 * 一键跑全部 E2E（scripts/e2e/*_test.mjs），输出 PASS/FAIL 汇总，任一失败退出码 1。
 *
 * 用法：
 *   node scripts/e2e/run-all.mjs                 # 全量（26 个）
 *   node scripts/e2e/run-all.mjs --only=dm,pay   # 只跑文件名前缀匹配的脚本
 */
import { spawnSync } from 'node:child_process'
import { readdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const onlyArg = process.argv.find(a => a.startsWith('--only='))
const only = onlyArg ? onlyArg.split('=')[1].split(',').map(s => s.trim()).filter(Boolean) : null

const tests = readdirSync(here)
  .filter(f => f.endsWith('_test.mjs'))
  .filter(f => !only || only.some(p => f.startsWith(p)))
  .sort()

if (tests.length === 0) {
  console.log('没有匹配的测试脚本')
  process.exit(1)
}

const TIMEOUT_MS = 180_000
let pass = 0
const failed = []

for (const t of tests) {
  const r = spawnSync('node', [join(here, t)], { timeout: TIMEOUT_MS, stdio: ['ignore', 'pipe', 'pipe'] })
  const out = (r.stdout?.toString() || '') + (r.stderr?.toString() || '')
  if (r.status === 0) {
    pass++
    console.log(`PASS  ${t}`)
  } else {
    failed.push(t)
    const reasons = out.split('\n').filter(l => l.includes('❌') || l.includes('异常')).slice(0, 3).join(' | ')
    console.log(`FAIL  ${t}  ${r.error?.code || ''} ${reasons}`)
  }
}

console.log('='.repeat(46))
console.log(`TOTAL ${pass}/${tests.length} PASS${failed.length ? '  FAILED: ' + failed.join(', ') : ''}`)
process.exit(failed.length ? 1 : 0)
