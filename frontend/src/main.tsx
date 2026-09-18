import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { AdminCacheReloadPage } from './components/AdminCacheReloadPage.tsx'
import { AdminAccessRequestApprovalPage } from './components/AdminAccessRequestApprovalPage.tsx'
import { Menu2Page } from './components/Menu2Page.tsx'
import { ConfirmDialogProvider } from './components/ConfirmDialog.tsx'
import { ExcelDownloadReasonDialogProvider } from './components/ExcelDownloadReasonDialog.tsx'
import { installApiFetchSecurity } from './adminSession.ts'

installApiFetchSecurity()

const normalizedPath = window.location.pathname.length > 1
  ? window.location.pathname.replace(/\/+$/, '')
  : window.location.pathname

if (normalizedPath === '/admin' || normalizedPath.startsWith('/admin/')) {
  const favicon = document.getElementById('app-favicon')
  if (favicon instanceof HTMLLinkElement) {
    favicon.type = 'image/x-icon'
    favicon.href = '/images/admin-branding/favicon-v3.ico'
  }
}

const rootContent = normalizedPath === '/admin/cachereload'
  ? <AdminCacheReloadPage />
  : normalizedPath === '/admin/access-request-approval'
    ? <AdminAccessRequestApprovalPage />
    : normalizedPath === '/admin/menu2'
      ? <Menu2Page />
      : <App />

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfirmDialogProvider>
      <ExcelDownloadReasonDialogProvider>
        {rootContent}
      </ExcelDownloadReasonDialogProvider>
    </ConfirmDialogProvider>
  </StrictMode>,
)
