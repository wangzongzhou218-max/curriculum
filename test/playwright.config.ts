import { defineConfig } from '@playwright/test'
import { fileURLToPath } from 'node:url'
export default defineConfig({
  testDir: './browser',
  fullyParallel: false,
  reporter: [['list'], ['html', { outputFolder: 'results/browser-report', open: 'never' }]],
  outputDir: 'results/browser-artifacts',
  use: { baseURL: 'http://localhost:3000', launchOptions: { executablePath: process.env.CHROME_PATH || fileURLToPath(new URL('../.tools/chrome-win64/chrome.exe', import.meta.url)) }, viewport: { width: 1366, height: 768 }, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  webServer: { command: 'pnpm --dir ../code/frontend dev', url: 'http://localhost:3000', reuseExistingServer: !process.env.CI, timeout: 60000 },
})
