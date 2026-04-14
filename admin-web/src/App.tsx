import { useEffect, useMemo, useState } from 'react'
import type { DragEvent, FormEvent } from 'react'
import { createClient, type Session, type SupabaseClient } from '@supabase/supabase-js'
import './App.css'

type ReleaseRow = {
  id: string
  package_name: string
  version_code: number
  version_name: string
  title: string
  changelog: string
  download_url: string
  force_update: boolean
  min_supported_version_code: number
  rollout_percent: number
  enabled: boolean
  created_at: string
}

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL as string | undefined
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined
const MAX_RELEASE_SIZE_BYTES = 200 * 1024 * 1024
const ALLOWED_RELEASE_EXTENSIONS = ['apk', 'msi', 'dmg', 'deb', 'exe', 'zip'] as const

function fileExtension(name: string): string {
  const idx = name.lastIndexOf('.')
  return idx >= 0 ? name.slice(idx + 1).toLowerCase() : ''
}

function guessMimeType(fileName: string): string {
  return (
    {
      apk: 'application/vnd.android.package-archive',
      msi: 'application/x-msi',
      dmg: 'application/x-apple-diskimage',
      deb: 'application/vnd.debian.binary-package',
      exe: 'application/vnd.microsoft.portable-executable',
      zip: 'application/zip',
    }[fileExtension(fileName)] ?? 'application/octet-stream'
  )
}
const COMMON_PACKAGE_NAMES = [
  'cn.verlu.sync',
  'cn.verlu.talk',
  'cn.verlu.music',
  'cn.verlu.doctor',
  'cn.verlu.cnchess',
  'cn.verlu.cloud',
  'cn.verlu.cloud.desktop',
] as const

function formatFileSize(bytes: number): string {
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function createSupabaseOrNull(): SupabaseClient | null {
  if (!supabaseUrl || !supabaseAnonKey) return null
  return createClient(supabaseUrl, supabaseAnonKey)
}

function App() {
  const supabase = useMemo(() => createSupabaseOrNull(), [])
  const [session, setSession] = useState<Session | null>(null)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [releases, setReleases] = useState<ReleaseRow[]>([])

  const [packageName, setPackageName] = useState('cn.verlu.sync')
  const [packagePreset, setPackagePreset] = useState('cn.verlu.sync')
  const [versionCode, setVersionCode] = useState('1')
  const [versionName, setVersionName] = useState('1.0.0')
  const [title, setTitle] = useState('版本更新')
  const [changelog, setChangelog] = useState('')
  const [rolloutPercent, setRolloutPercent] = useState('100')
  const [minSupportedVersionCode, setMinSupportedVersionCode] = useState('0')
  const [forceUpdate, setForceUpdate] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [dragActive, setDragActive] = useState(false)

  useEffect(() => {
    if (!supabase) return
    supabase.auth.getSession().then(({ data }) => setSession(data.session ?? null))
    const { data: subscription } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      setSession(nextSession)
    })
    return () => {
      subscription.subscription.unsubscribe()
    }
  }, [supabase])

  useEffect(() => {
    if (!supabase || !session) return
    void loadReleases()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [supabase, session])

  async function loadReleases() {
    if (!supabase) return
    const { data, error: loadError } = await supabase
      .from('app_releases')
      .select('*')
      .order('created_at', { ascending: false })
      .limit(200)
    if (loadError) {
      setError(loadError.message)
      return
    }
    setReleases((data ?? []) as ReleaseRow[])
  }

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!supabase) return
    setBusy(true)
    setError(null)
    const { error: loginError } = await supabase.auth.signInWithPassword({
      email: email.trim(),
      password,
    })
    if (loginError) setError(loginError.message)
    setBusy(false)
  }

  async function handleOAuthLogin(provider: 'google' | 'github') {
    if (!supabase) return
    setBusy(true)
    setError(null)
    const { error: oauthError } = await supabase.auth.signInWithOAuth({
      provider,
      options: {
        redirectTo: window.location.origin,
      },
    })
    if (oauthError) {
      setError(oauthError.message)
      setBusy(false)
    }
  }

  async function handlePublish(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!supabase || !session) return
    if (!file) {
      setError('请选择 APK 文件')
      return
    }
    setBusy(true)
    setError(null)
    const cleanFileName = file.name.replace(/\s+/g, '-')
    const storagePath = `${packageName}/${versionCode}-${Date.now()}-${cleanFileName}`

    const { error: uploadError } = await supabase.storage
      .from('app-releases')
      .upload(storagePath, file, {
        upsert: true,
        contentType: file.type || guessMimeType(file.name),
      })
    if (uploadError) {
      setBusy(false)
      const raw = uploadError.message ?? ''
      if (raw.toLowerCase().includes('exceeded the maximum allowed size')) {
        setError(
          '上传失败：当前 Supabase 项目全局上传上限可能低于文件大小（Free 计划常见为 50MB）。请在 Storage Settings 调整全局限制或升级套餐。',
        )
      } else {
        setError(raw)
      }
      return
    }

    const { data: urlData } = supabase.storage.from('app-releases').getPublicUrl(storagePath)

    const { error: insertError } = await supabase.from('app_releases').insert({
      package_name: packageName.trim(),
      version_code: Number(versionCode),
      version_name: versionName.trim(),
      title: title.trim() || '版本更新',
      changelog: changelog.trim(),
      download_url: urlData.publicUrl,
      file_size_bytes: file.size,
      force_update: forceUpdate,
      min_supported_version_code: Number(minSupportedVersionCode),
      rollout_percent: Number(rolloutPercent),
      enabled: true,
      created_by: session.user.id,
    })
    setBusy(false)
    if (insertError) {
      setError(insertError.message)
      return
    }
    setFile(null)
    await loadReleases()
  }

  function acceptReleaseFile(selectedFile: File | null) {
    if (!selectedFile) return
    const ext = fileExtension(selectedFile.name)
    const isSupported = ALLOWED_RELEASE_EXTENSIONS.includes(ext as (typeof ALLOWED_RELEASE_EXTENSIONS)[number])
    if (!isSupported) {
      setError('仅支持 apk / msi / dmg / deb / exe / zip')
      return
    }
    if (selectedFile.size > MAX_RELEASE_SIZE_BYTES) {
      setError('安装包文件不能超过 200MB')
      return
    }
    setError(null)
    setFile(selectedFile)
  }

  function handleDragOver(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setDragActive(true)
  }

  function handleDragLeave(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setDragActive(false)
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setDragActive(false)
    acceptReleaseFile(event.dataTransfer.files?.[0] ?? null)
  }

  async function toggleEnabled(row: ReleaseRow) {
    if (!supabase) return
    setBusy(true)
    setError(null)
    const { error: updateError } = await supabase
      .from('app_releases')
      .update({ enabled: !row.enabled })
      .eq('id', row.id)
    setBusy(false)
    if (updateError) {
      setError(updateError.message)
      return
    }
    await loadReleases()
  }

  async function handleLogout() {
    if (!supabase) return
    await supabase.auth.signOut()
    setReleases([])
  }

  if (!supabase) {
    return (
      <main className="page">
        <h1>版本发布后台</h1>
        <p className="error">缺少环境变量：VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY</p>
      </main>
    )
  }

  return (
    <main className="page">
      <header className="header">
        <h1>版本发布后台</h1>
        {session ? (
          <button onClick={handleLogout} disabled={busy}>
            退出登录
          </button>
        ) : null}
      </header>

      {error ? <p className="error">{error}</p> : null}

      {!session ? (
        <div className="card form">
          <h2>管理员登录</h2>
          <div className="grid">
            <button type="button" onClick={() => handleOAuthLogin('google')} disabled={busy}>
              使用 Google 登录
            </button>
            <button type="button" onClick={() => handleOAuthLogin('github')} disabled={busy}>
              使用 GitHub 登录
            </button>
          </div>
          <p>或使用邮箱密码登录</p>
          <form onSubmit={handleLogin}>
            <label>
              邮箱
              <input value={email} onChange={(e) => setEmail(e.target.value)} required />
            </label>
            <label>
              密码
              <input
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                type="password"
                required
              />
            </label>
            <button type="submit" disabled={busy}>
              登录
            </button>
          </form>
        </div>
      ) : (
        <>
          <form className="card form" onSubmit={handlePublish}>
            <h2>发布新版本</h2>
            <div className="grid">
              <label>
                包名
                <div className="package-picker">
                  <select
                    value={packagePreset}
                    onChange={(e) => {
                      const value = e.target.value
                      setPackagePreset(value)
                      if (value !== '__custom__') {
                        setPackageName(value)
                      }
                    }}
                  >
                    {COMMON_PACKAGE_NAMES.map((pkg) => (
                      <option key={pkg} value={pkg}>
                        {pkg}
                      </option>
                    ))}
                    <option value="__custom__">自定义包名</option>
                  </select>
                  <input
                    value={packageName}
                    onChange={(e) => {
                      setPackageName(e.target.value)
                      if (!COMMON_PACKAGE_NAMES.includes(e.target.value as (typeof COMMON_PACKAGE_NAMES)[number])) {
                        setPackagePreset('__custom__')
                      }
                    }}
                    list="packageNameSuggestions"
                    required
                  />
                </div>
              </label>
              <label>
                versionCode
                <input
                  value={versionCode}
                  onChange={(e) => setVersionCode(e.target.value)}
                  type="number"
                  min={1}
                  required
                />
              </label>
              <label>
                versionName
                <input value={versionName} onChange={(e) => setVersionName(e.target.value)} required />
              </label>
              <label>
                灰度比例（1-100）
                <input
                  value={rolloutPercent}
                  onChange={(e) => setRolloutPercent(e.target.value)}
                  type="number"
                  min={1}
                  max={100}
                  required
                />
              </label>
              <label>
                最低支持 versionCode
                <input
                  value={minSupportedVersionCode}
                  onChange={(e) => setMinSupportedVersionCode(e.target.value)}
                  type="number"
                  min={0}
                  required
                />
              </label>
              <label>
                标题
                <input value={title} onChange={(e) => setTitle(e.target.value)} required />
              </label>
            </div>
            <label>
              更新说明
              <textarea value={changelog} onChange={(e) => setChangelog(e.target.value)} rows={5} />
            </label>
            <label>安装包文件</label>
            <div
              className={`dropzone${dragActive ? ' active' : ''}`}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
            >
              <p>拖拽安装包到这里，或点击下方选择文件（apk/msi/dmg/deb/exe/zip）</p>
              <input
                type="file"
                accept=".apk,.msi,.dmg,.deb,.exe,.zip"
                onChange={(e) => acceptReleaseFile(e.target.files?.[0] ?? null)}
              />
              <p className="file-name">
                {file ? `已选择：${file.name}（${formatFileSize(file.size)}）` : '未选择文件'}
              </p>
            </div>
            <label className="checkbox">
              <input
                type="checkbox"
                checked={forceUpdate}
                onChange={(e) => setForceUpdate(e.target.checked)}
              />
              强制更新
            </label>
            <button type="submit" disabled={busy}>
              上传并发布
            </button>
          </form>
          <datalist id="packageNameSuggestions">
            {COMMON_PACKAGE_NAMES.map((pkg) => (
              <option key={pkg} value={pkg} />
            ))}
          </datalist>

          <section className="card">
            <h2>版本列表</h2>
            <table>
              <thead>
                <tr>
                  <th>包名</th>
                  <th>版本</th>
                  <th>策略</th>
                  <th>下载</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {releases.map((row) => (
                  <tr key={row.id}>
                    <td>{row.package_name}</td>
                    <td>
                      {row.version_name} ({row.version_code})
                    </td>
                    <td>
                      {row.force_update ? '强更' : '可选'} / 灰度 {row.rollout_percent}% / 最低
                      {row.min_supported_version_code}
                    </td>
                    <td>
                      <a href={row.download_url} target="_blank" rel="noreferrer">
                        下载
                      </a>
                    </td>
                    <td>{row.enabled ? '生效中' : '已停用'}</td>
                    <td>
                      <button onClick={() => toggleEnabled(row)} disabled={busy}>
                        {row.enabled ? '停用' : '启用'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </>
      )}
    </main>
  )
}

export default App
