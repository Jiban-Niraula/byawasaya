import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { QRCodeSVG } from 'qrcode.react'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const TENANT_BASE_DOMAIN = (import.meta.env.VITE_BASE_DOMAIN ?? '').toLowerCase()

function isTenantSubdomainHost() {
  const hostname = window.location.hostname.toLowerCase()
  if (!TENANT_BASE_DOMAIN || !hostname.endsWith(`.${TENANT_BASE_DOMAIN}`)) return false
  const label = hostname.slice(0, -(TENANT_BASE_DOMAIN.length + 1))
  return !label.includes('.') && /^[a-z0-9][a-z0-9-]{1,58}[a-z0-9]$/.test(label)
    && !['www', 'app', 'admin', 'api', 'mail', 'status', 'support'].includes(label)
}

type Role = 'SUPERADMIN' | 'PLATFORM_ADMIN' | 'PLATFORM_SUPPORT' | 'STORE_OWNER' | 'STORE_STAFF' | 'CUSTOMER'
type AuthUser = { username: string; role: Role; businessId: string | null; storeSlug: string | null; storeStatus?: string | null; emailVerified?: boolean; mfaEnabled?: boolean }
type AuthPayload = { expiresIn: number; user: AuthUser | null; mfaRequired?: boolean; challengeToken?: string | null; challengeMethod?: 'EMAIL' | 'TOTP' }
type SessionPayload = { authenticated: boolean; user: AuthUser; expiresAt: string }
type Business = { id: string; name: string; slug: string; ownerName: string; ownerEmail?: string; phone?: string; plan: string; status: string; createdAt: string }
type PlatformUser = { id: string; username: string; role: string; businessId: string; businessName: string; enabled: boolean; createdAt: string }
type AdminOverview = { totalBusinesses: number; activeBusinesses: number; pendingReviews: number; totalOrders: number; grossSales: number; recentBusinesses: Business[] }
type PaymentPolicy = 'COD_ALLOWED' | 'ONLINE_ONLY' | 'ADVANCE_REQUIRED'
type PaymentProvider = 'ESEWA' | 'KHALTI'
type ProviderState = { configured: boolean; enabled: boolean; sandbox: boolean }
type PaymentSettings = { esewa: ProviderState; khalti: ProviderState; onlinePaymentEnabled: boolean }
type ProductVariant = { id: string; label: string; sku?: string; retailPrice: number | null; wholesalePrice: number | null; active: boolean }
type Product = { id: string; businessId: string; name: string; slug: string; sku?: string; category: string; description?: string; retailPrice: number; wholesalePrice: number; wholesaleMinQuantity: number; imageUrl?: string; paymentPolicy: PaymentPolicy; advancePaymentPercent: number; active: boolean; createdAt: string; variants: ProductVariant[] }
type Store = { id?: string; name: string; slug: string; logoUrl?: string | null; ownerName?: string; ownerEmail?: string; phone?: string; description?: string; plan?: string; status?: string; templateKey: string; primaryColor: string; codEnabled: boolean; onlinePaymentEnabled: boolean; advancePaymentPercent: number; deliveryFee: number; minimumOrderAmount: number; paymentProviders?: PaymentProvider[]; products?: Product[] }
type OrderItem = { productId: string; variantId?: string | null; variantLabel?: string | null; productName: string; sku?: string; quantity: number; unitPrice: number; lineTotal: number }
type Order = { id: string; orderNumber: string; customerName: string; phone: string; email?: string; province: string; district: string; municipality?: string; addressLine: string; salesType: string; paymentMethod: string; paymentStatus: string; status: string; subtotal: number; deliveryFee: number; advanceAmount: number; totalAmount: number; createdAt: string; items: OrderItem[] }
type CheckoutResponse = { order: Order; provider?: PaymentProvider | null; redirectUrl?: string | null; redirectMethod?: 'GET' | 'POST' | null; redirectFields?: Record<string, string> }
type PaymentAttempt = { status: string; provider: PaymentProvider; orderNumber: string; amountPaisa: number; currency: string }
type ProductInput = { name: string; sku: string; category: string; description: string; retailPrice: string; wholesalePrice: string; wholesaleMinQuantity: string; imageUrl: string; paymentPolicy: PaymentPolicy; advancePaymentPercent: string; variantsText: string }
type StoreInput = { name: string; phone: string; description: string; templateKey: string; primaryColor: string; codEnabled: boolean; onlinePaymentEnabled: boolean; advancePaymentPercent: string; deliveryFee: string; minimumOrderAmount: string }
type CartLine = { product: Product; variant?: ProductVariant; quantity: number }
type PlanOption = { id: string; code: string; name: string; description?: string; monthlyPriceNpr: number; annualPriceNpr: number; trialDays: number; maxProducts: number; maxStaff: number; published: boolean }
type AppDocument = { id: string; documentType: string; originalFilename: string; contentType: string; sizeBytes: number; verificationStatus: string; reviewerNote?: string; uploadedAt: string }
type TenantApplication = { id: string; businessId: string; storeCode: string; storeName: string; slug: string; ownerName: string; ownerEmail: string | null; phone: string; emailVerified: boolean; citizenshipNumber: string; businessType: string; panVatNumber?: string; registrationNumber: string; registrationAuthority: string; registeredAddress: string; province: string; district: string; municipality: string; selectedPlanCode: string; selectedPlanPriceNpr: number; selectedTemplateKey: string; promoCode?: string; status: string; applicantNotes?: string; reviewerNotes?: string; publicReviewNotes?: string; reviewerUsername?: string; submittedAt?: string; reviewedAt?: string; createdAt: string; documents: AppDocument[] }
type Promo = { id: string; code: string; description?: string; discountType: string; discountValue: number; startsAt: string; endsAt?: string; maxRedemptions?: number; maxPerTenant: number; redeemedCount: number; active: boolean; createdAt: string }
type PlatformUserRecord = { id: string; username: string; fullName: string; role: string; enabled: boolean; mfaEnabled: boolean; createdAt: string }
type MfaStatus = { enabled: boolean; recoveryCodesRemaining: number }
type MfaEnrollment = { secret: string; provisioningUri: string; expiresAt: string }
type AssetResponse = { id: string; url: string; contentType: string; sizeBytes: number }
function isPlatformRole(role: Role) { return ['SUPERADMIN', 'PLATFORM_ADMIN', 'PLATFORM_SUPPORT'].includes(role) }

function navigate(path: string) {
  window.history.pushState({}, '', path)
  window.dispatchEvent(new PopStateEvent('popstate'))
  window.scrollTo({ top: 0, behavior: 'instant' })
}

let csrfToken: string | null = null

async function ensureCsrfToken() {
  if (csrfToken) return csrfToken
  const response = await fetch(`${API_BASE_URL}/api/auth/csrf`, { credentials: 'include' })
  const body = await response.json().catch(() => null)
  if (!response.ok || !body?.token) throw new Error('Could not establish a secure request token. Refresh and try again.')
  csrfToken = body.token as string
  return csrfToken
}

async function api<T>(path: string, token?: string | null, init: RequestInit = {}): Promise<T> {
  void token // Kept in the helper signature during migration of existing feature calls; credentials now use HttpOnly cookies.
  const headers = new Headers(init.headers)
  const method = (init.method ?? 'GET').toUpperCase()
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) headers.set('X-XSRF-TOKEN', await ensureCsrfToken())
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers, credentials: 'include' })
  } catch {
    throw new Error('The API could not be reached. Start the backend and check VITE_API_BASE_URL.')
  }
  if (response.ok && ['/api/auth/login', '/api/auth/mfa/verify-login', '/api/customer/auth/login'].includes(path)) csrfToken = null
  const body = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) {
    const fields = body?.fields ? Object.values(body.fields).join(' ') : ''
    throw new Error([body?.message, fields].filter(Boolean).join(' ') || `Request failed (${response.status}).`)
  }
  return body as T
}

function money(value: number | string | undefined) {
  return new Intl.NumberFormat('en-NP', { style: 'currency', currency: 'NPR', maximumFractionDigits: 0 }).format(Number(value ?? 0))
}
function moneyExact(value: number | string | undefined) {
  return new Intl.NumberFormat('en-NP', { style: 'currency', currency: 'NPR', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(value ?? 0))
}
function dateInputAfter(days: number) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
function dateOnly(value: string) {
  if (!value || value === 'undefined' || value === 'null') return '—'
  return new Intl.DateTimeFormat('en-NP', { dateStyle: 'medium', timeZone: 'Asia/Kathmandu' }).format(new Date(`${value.slice(0, 10)}T00:00:00+05:45`))
}
function dateLabel(value?: string) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('en-NP', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}
function pretty(value: string) { return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, c => c.toUpperCase()) }
function productEmoji(category: string) {
  const c = category.toLowerCase()
  if (c.includes('food') || c.includes('tea')) return '🍵'
  if (c.includes('cloth') || c.includes('wear')) return '🧣'
  if (c.includes('station')) return '📓'
  if (c.includes('access')) return '👜'
  if (c.includes('home')) return '🏺'
  if (c.includes('beauty')) return '🌿'
  return '✳️'
}
function initials(name: string) { return name.split(/\s+/).slice(0, 2).map(s => s[0]).join('').toUpperCase() }
function slugify(value: string) { return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') }

function Logo({ light = false }: { light?: boolean }) {
  return <a className={`brand-lockup ${light ? 'brand-lockup-light' : ''}`} href="/" onClick={e => { e.preventDefault(); navigate('/') }}><span className="brand-symbol">b<span>.</span></span><span className="brand-word">byawasaya</span></a>
}
function Icon({ children }: { children: ReactNode }) { return <span className="inline-icon" aria-hidden="true">{children}</span> }
function Pill({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'green' | 'amber' | 'blue' | 'red' }) { return <span className={`pill pill-${tone}`}>{children}</span> }
function Button({ children, variant = 'primary', ...props }: React.ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'ghost' | 'danger' }) { return <button {...props} className={`button button-${variant} ${props.className ?? ''}`.trim()}>{children}</button> }
function EmptyState({ title, text, action }: { title: string; text: string; action?: ReactNode }) { return <div className="empty-state"><div className="empty-icon">✳</div><strong>{title}</strong><p>{text}</p>{action}</div> }

function Landing() {
  return <div className="landing">
    <header className="public-nav page-container"><Logo /><nav><a href="#features">Platform</a><a href="#how-it-works">How it works</a><a href="#nepal">Made for Nepal</a></nav><div className="nav-actions"><button className="nav-link" onClick={() => navigate('/tenant/login')}>Sign in</button><Button onClick={() => navigate('/register')}>Create a store <Icon>↗</Icon></Button></div></header>
    <main>
      <section className="hero page-container">
        <div className="hero-copy"><Pill tone="green">NEPAL'S COMMERCE WORKSPACE</Pill><h1>Your business deserves<br />a <em>better storefront.</em></h1><p>Launch your online shop, manage products and wholesale pricing, and keep every order in one place. No coding. No complicated setup.</p><div className="hero-buttons"><Button onClick={() => navigate('/register')}>Start your store <Icon>↗</Icon></Button><button className="button button-secondary" onClick={() => navigate('/store/himalayan-goods')}>Explore demo store <Icon>→</Icon></button></div><div className="hero-assurance"><span className="assurance-check">✓</span><span>Built for Nepali businesses</span><span className="assurance-dot">·</span><span>Retail + wholesale</span></div></div>
        <div className="hero-visual"><div className="hero-orbit orbit-one"/><div className="hero-orbit orbit-two"/><div className="hero-store-window"><div className="store-window-top"><span className="window-dots"><i/><i/><i/></span><span>yourstore.byawasaya.com</span><span className="window-menu">•••</span></div><div className="store-window-banner"><span>THE EVERYDAY COLLECTION</span><b>Good things,<br /><i>made thoughtfully.</i></b><small>Explore the collection ↗</small></div><div className="mini-products"><div className="mini-product mini-product-sand"><span>👜</span><small>Dhaka tote</small><b>Rs. 1,450</b></div><div className="mini-product mini-product-mint"><span>🍵</span><small>Himalayan tea</small><b>Rs. 1,850</b></div><div className="mini-product mini-product-lilac"><span>📓</span><small>Lokta notebooks</small><b>Rs. 680</b></div></div><div className="store-window-foot"><span>✦ Secure store setup</span><span>Cash on delivery supported</span></div></div><div className="floating-note note-orders"><span className="note-icon">↗</span><span><small>Orders today</small><b>+ 12 new</b></span></div><div className="floating-note note-payment"><span className="note-icon note-icon-green">✓</span><span><small>Checkout rules</small><b>Your way, per product</b></span></div></div>
      </section>
      <section className="trust-strip"><div className="page-container trust-strip-inner"><span>DESIGNED FOR THE WAY NEPAL DOES BUSINESS</span><strong>Local sellers</strong><i/><strong>Retail & wholesale</strong><i/><strong>Cash on delivery</strong><i/><strong>One simple dashboard</strong></div></section>
      <section className="features-section page-container" id="features"><div className="section-heading"><Pill>THE ESSENTIALS, ALL CONNECTED</Pill><h2>Everything you need to<br /><em>sell with confidence.</em></h2><p>Start with a straightforward storefront. Grow into the tools your business needs, when you're ready.</p></div><div className="feature-grid"><article className="feature-card feature-card-highlight"><div className="feature-icon">▦</div><span className="feature-kicker">01 / STORE</span><h3>Your own online shop</h3><p>Get a clean, ready-made storefront with your store name, products and a shareable link. No custom development required.</p><div className="feature-demo"><span className="demo-dot"/><span className="demo-url">byawasaya.com/store/your-shop</span><span className="demo-arrow">↗</span></div></article><article className="feature-card"><div className="feature-icon icon-orange">◈</div><span className="feature-kicker">02 / CATALOG</span><h3>Retail and wholesale</h3><p>Set retail prices and wholesale rates with minimum quantities. Keep your catalog clear for every kind of buyer.</p><div className="feature-price"><span>Retail <b>Rs. 1,450</b></span><span>Wholesale <b>Rs. 1,120</b></span></div></article><article className="feature-card"><div className="feature-icon icon-blue">⇄</div><span className="feature-kicker">03 / CHECKOUT</span><h3>Payment rules that fit</h3><p>Offer cash on delivery, configure an advance requirement, or mark selected products as online-payment-only.</p><div className="payment-chips"><span>COD</span><span>Advance + COD</span><span>Online</span></div></article><article className="feature-card"><div className="feature-icon icon-lilac">▤</div><span className="feature-kicker">04 / OPERATIONS</span><h3>Orders in one place</h3><p>See customer details, order totals and payment status. Move orders through confirmed, processing, shipped and delivered.</p><div className="order-demo"><span><i/> BYW-261014</span><b>Rs. 2,340</b><Pill tone="amber">Placed</Pill></div></article></div></section>
      <section className="pricing-section page-container" id="pricing"><div className="section-heading"><Pill tone="green">SIMPLE, TRANSPARENT PRICING</Pill><h2>Choose the space your<br /><em>business needs.</em></h2><p>Every plan includes a storefront, product catalog, order management and a dedicated workspace.</p></div><div className="pricing-grid"><article className="pricing-card"><span className="pricing-label">STARTER</span><h3>NPR 2,500 <small>/ month</small></h3><p>For new businesses getting online.</p><Button onClick={() => navigate('/register')}>Start with Starter <Icon>↗</Icon></Button></article><article className="pricing-card pricing-card-featured"><span className="pricing-label">PLUS <b>POPULAR</b></span><h3>NPR 3,500 <small>/ month</small></h3><p>For growing stores that need more room.</p><Button onClick={() => navigate('/register')}>Choose Plus <Icon>↗</Icon></Button></article><article className="pricing-card"><span className="pricing-label">PRO</span><h3>NPR 5,000 <small>/ month</small></h3><p>For established teams operating at scale.</p><Button onClick={() => navigate('/register')}>Choose Pro <Icon>↗</Icon></Button></article></div></section>
      <section className="workflow-section" id="how-it-works"><div className="page-container workflow-layout"><div className="section-heading section-heading-left"><Pill tone="green">SIMPLE BY DESIGN</Pill><h2>From idea to<br /><em>first order.</em></h2><p>Start small. Get online. Manage your day-to-day from the same workspace.</p><Button onClick={() => navigate('/register')}>Create your store <Icon>↗</Icon></Button></div><div className="workflow-steps"><div className="workflow-step"><span>01</span><div><h3>Choose your store name</h3><p>Set up an account, choose a unique store link, and add your business details.</p></div><b>↗</b></div><div className="workflow-step"><span>02</span><div><h3>Add your products</h3><p>Set prices, descriptions, wholesale minimums, and per-product payment rules.</p></div><b>↗</b></div><div className="workflow-step"><span>03</span><div><h3>Share and manage</h3><p>Share your storefront link, receive orders, and update fulfilment from your dashboard.</p></div><b>↗</b></div></div></div></section>
      <section className="nepal-section page-container" id="nepal"><div className="nepal-art"><div className="nepal-sun"/><div className="mountain mountain-back"/><div className="mountain mountain-front"/><div className="nepal-art-label"><span>MADE FOR LOCAL COMMERCE</span><b>From your neighborhood<br />to all of Nepal.</b></div></div><div className="nepal-copy"><Pill>BUILT WITH NEPAL IN MIND</Pill><h2>Commerce tools that<br /><em>understand your work.</em></h2><p>Rupee-based pricing, local delivery details, retail and wholesale selling, and checkout options that reflect the way real businesses operate.</p><div className="nepal-checks"><span><i>✓</i> NPR pricing</span><span><i>✓</i> Nepal address fields</span><span><i>✓</i> COD and advance-payment rules</span><span><i>✓</i> No-code fixed storefront template</span></div></div></section>
      <section className="final-cta page-container"><div><Pill tone="green">YOUR NEXT CHAPTER STARTS HERE</Pill><h2>Make room for<br /><em>more business.</em></h2><p>Bring your products online and keep the everyday work simple.</p></div><div className="final-cta-actions"><Button onClick={() => navigate('/register')}>Launch your store <Icon>↗</Icon></Button><button className="button button-ghost" onClick={() => navigate('/tenant/login')}>Already have an account? Sign in</button></div><span className="cta-watermark">b.</span></section>
    </main>
    <footer className="public-footer page-container"><Logo /><span>© 2026 Byawasaya · Business, made digital.</span><div><a href="#features">Platform</a><button onClick={() => navigate('/tenant/login')}>Tenant sign in</button><button onClick={() => navigate('/admin/login')}>Platform administration</button></div></footer>
  </div>
}

function AuthLayout({ children, title, subtitle, sideTitle, sideText, wide = false }: { children: ReactNode; title: string; subtitle: string; sideTitle: string; sideText: string; wide?: boolean }) {
  return <div className={`auth-page ${wide ? 'auth-page-wide' : ''}`}><aside className="auth-aside"><Logo light/><div className="auth-aside-content"><div className="auth-illustration"><div className="auth-illustration-card auth-card-back"><span>STORE SETTINGS</span><b>Payments</b><i>COD · Advance · Online</i></div><div className="auth-illustration-card auth-card-front"><span className="auth-card-check">✓</span><b>Your store is ready.</b><small>One workspace. Less busywork.</small></div><span className="auth-sparkle sparkle-a">✳</span><span className="auth-sparkle sparkle-b">✦</span></div><Pill tone="green">COMMERCE, MADE SIMPLER</Pill><h2>{sideTitle}</h2><p>{sideText}</p></div><div className="auth-aside-footer"><span>Made for Nepal</span><span>© Byawasaya 2026</span></div></aside><main className="auth-main"><div className="auth-mobile-brand"><Logo/></div><div className={`auth-main-inner ${wide ? 'auth-main-inner-wide' : ''}`}><div className="auth-heading"><button className="back-link" onClick={() => navigate('/')}>← Back to home</button><h1>{title}</h1><p>{subtitle}</p></div>{children}</div></main></div>
}

function PlatformMfaSettings({ token, user, onEnabled, onLogout }: { token: string; user: AuthUser; onEnabled: () => void; onLogout: () => void }) {
  const [status, setStatus] = useState<MfaStatus | null>(null)
  const [enrollment, setEnrollment] = useState<MfaEnrollment | null>(null)
  const [codes, setCodes] = useState<string[]>([])
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  useEffect(() => {
    let active = true
    api<MfaStatus>('/api/auth/mfa/status', token).then(value => { if (active) setStatus(value) }).catch(err => { if (active) setError(err instanceof Error ? err.message : 'Could not load MFA status.') })
    return () => { active = false }
  }, [token])
  async function begin(e: FormEvent) {
    e.preventDefault(); setLoading(true); setError('')
    try { setEnrollment(await api<MfaEnrollment>('/api/auth/mfa/enrollment', token, { method: 'POST', body: JSON.stringify({ password }) })); setPassword('') }
    catch (err) { setError(err instanceof Error ? err.message : 'Could not start authenticator setup.') }
    finally { setLoading(false) }
  }
  async function enable(e: FormEvent) {
    e.preventDefault(); setLoading(true); setError('')
    try {
      const result = await api<{ recoveryCodes: string[] }>('/api/auth/mfa/enable', token, { method: 'POST', body: JSON.stringify({ code: code.trim() }) })
      setCodes(result.recoveryCodes); setStatus({ enabled: true, recoveryCodesRemaining: result.recoveryCodes.length }); setEnrollment(null); setCode('')
    } catch (err) { setError(err instanceof Error ? err.message : 'Could not enable MFA.') }
    finally { setLoading(false) }
  }
  async function disable(e: FormEvent) {
    e.preventDefault(); setLoading(true); setError('')
    try { await api('/api/auth/mfa/disable', token, { method: 'POST', body: JSON.stringify({ password, code: code.trim() }) }); await onLogout() }
    catch (err) { setError(err instanceof Error ? err.message : 'Could not remove authenticator.') }
    finally { setLoading(false) }
  }
  function downloadCodes() {
    const blob = new Blob([`Byawasaya platform MFA recovery codes\nAccount: ${user.username}\n\n${codes.join('\n')}\n`], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob); const link = document.createElement('a')
    link.href = url; link.download = 'byawasaya-mfa-recovery-codes.txt'; link.click(); URL.revokeObjectURL(url)
  }
  const content = <section className="surface-card page-table-card mfa-settings-card">
    {error && <div className="alert alert-error" role="alert">{error}</div>}
    {codes.length > 0 ? <><div className="surface-heading"><div><h2>Save your recovery codes</h2><p>Each code works once. They are shown only now; keep them somewhere private.</p></div><Pill tone="amber">Shown once</Pill></div><div className="recovery-code-list">{codes.map(item => <code key={item}>{item}</code>)}</div><div className="product-form-actions"><Button variant="secondary" onClick={downloadCodes}>Download codes</Button><Button onClick={onEnabled}>I saved these codes</Button></div></>
      : !status ? <p>Loading multi-factor settings…</p>
      : !status.enabled ? <><div className="surface-heading"><div><h2>Authenticator setup is optional</h2><p>Email OTP already protects every platform sign-in. You can add an authenticator here; enrolled accounts use it for future sign-ins.</p></div><Pill tone="blue">Optional</Pill></div>
        {!enrollment ? <form className="auth-form" onSubmit={begin}><label>Confirm your password<input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" required maxLength={72}/></label><Button type="submit" disabled={loading}>{loading ? 'Starting…' : 'Start authenticator setup'}</Button></form>
          : <form className="auth-form" onSubmit={enable}><div className="notice-note"><span>i</span><p>Scan this QR code with Google Authenticator, Microsoft Authenticator, or another compatible app. You can also add the account manually with the secret below.</p></div><div className="totp-qr"><QRCodeSVG value={enrollment.provisioningUri} size={208} includeMargin/></div><label>Authenticator secret<code className="totp-secret">{enrollment.secret}</code></label><a className="subtle-link" href={enrollment.provisioningUri}>Open authenticator app ↗</a><label>Six-digit verification code<input inputMode="numeric" autoComplete="one-time-code" value={code} onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))} pattern="[0-9]{6}" maxLength={6} required/></label><small className="field-help">Setup expires {new Date(enrollment.expiresAt).toLocaleTimeString()}.</small><Button type="submit" disabled={loading || code.length !== 6}>{loading ? 'Verifying…' : 'Enable MFA'}</Button></form>}
      </>
      : <><div className="surface-heading"><div><h2>Authenticator verification is active</h2><p>Platform sign-ins require your authenticator or one of the remaining recovery codes.</p></div><Pill tone="green">Enabled</Pill></div><div className="activity-placeholder"><span className="large-stat">{status.recoveryCodesRemaining}</span><div><b>Unused recovery codes</b><p>Ask another superadmin to reset your authenticator if you lose both the app and all recovery codes.</p></div></div><form className="auth-form" onSubmit={disable}><h3>Remove authenticator</h3><p className="quiet-footnote">This revokes your current sessions. Your next platform sign-in will use email OTP.</p><label>Confirm your password<input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" required maxLength={72}/></label><label>Authenticator or recovery code<input value={code} onChange={e => setCode(e.target.value)} autoComplete="one-time-code" autoCapitalize="characters" maxLength={32} required/></label><Button type="submit" variant="danger" disabled={loading}>{loading ? 'Checking…' : 'Remove authenticator and sign out'}</Button></form></>}
  </section>
  return content
}

function Login({ onLogin, accountType }: { onLogin: (token: string, user: AuthUser) => void; accountType: 'PLATFORM' | 'TENANT' }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [challengeToken, setChallengeToken] = useState('')
  const [verificationCode, setVerificationCode] = useState('')
  const [verificationMethod, setVerificationMethod] = useState<'email' | 'authenticator'>('email')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  async function submit(e: FormEvent) {
    e.preventDefault(); setError(''); setLoading(true)
    try {
      const data = await api<AuthPayload>('/api/auth/login', null, { method: 'POST', body: JSON.stringify({ identifier: username, password, accountType }) })
      if (data.mfaRequired && data.challengeToken) { setChallengeToken(data.challengeToken); setPassword(''); const method = data.challengeMethod ?? 'EMAIL'; setVerificationMethod(method === 'TOTP' ? 'authenticator' : 'email'); return }
      if (!data.user) throw new Error('Sign-in could not be completed.')
      onLogin('cookie', data.user)
    }
    catch (err) { setError(err instanceof Error ? err.message : 'Unable to sign in.') }
    finally { setLoading(false) }
  }
  async function verifySecondFactor(e: FormEvent) {
    e.preventDefault(); setError(''); setLoading(true)
    try {
      const data = await api<AuthPayload>('/api/auth/mfa/verify-login', null, { method: 'POST', body: JSON.stringify({ challengeToken, code: verificationCode.trim() }) })
      if (!data.user) throw new Error('Sign-in could not be completed.')
      onLogin('cookie', data.user)
    } catch (err) { setError(err instanceof Error ? err.message : 'The verification code was not accepted.') }
    finally { setLoading(false) }
  }
  return <AuthLayout title={challengeToken ? 'Verify your sign in.' : accountType === 'PLATFORM' ? "Platform administrator sign in." : "Welcome back."} subtitle={challengeToken ? verificationMethod === 'email' ? 'A six-digit code was sent to your email address.' : 'Enter a current Google Authenticator code or one of your recovery codes.' : accountType === 'PLATFORM' ? "Sign in to manage Byawasaya." : "Sign in to your tenant workspace."} sideTitle={challengeToken ? 'A second check protects your account.' : accountType === 'PLATFORM' ? "Protect the whole platform." : "Good businesses deserve a good system."} sideText={challengeToken ? 'This challenge expires shortly and does not create a session until verification succeeds.' : accountType === 'PLATFORM' ? "Review store applications, manage plans and keep the platform secure." : "Your products, orders, customers and storefront settings—kept together, right where you need them."}>
    {challengeToken ? <form className="auth-form" onSubmit={verifySecondFactor}><label>{verificationMethod === 'email' ? 'Email verification code' : 'Google Authenticator or recovery code'}<input inputMode={verificationMethod === 'email' ? 'numeric' : 'text'} value={verificationCode} onChange={e => setVerificationCode(verificationMethod === 'email' ? e.target.value.replace(/\D/g, '').slice(0, 6) : e.target.value)} autoComplete="one-time-code" autoCapitalize="characters" maxLength={verificationMethod === 'email' ? 6 : 32} required autoFocus/></label>{error && <div className="alert alert-error" role="alert">{error}</div>}<Button type="submit" disabled={loading || !verificationCode.trim()} className="button-wide">{loading ? 'Verifying…' : 'Verify and sign in'} <Icon>↗</Icon></Button><p className="auth-switch"><button type="button" onClick={() => { setChallengeToken(''); setVerificationCode(''); setError('') }}>Start over</button></p></form> : <form className="auth-form" onSubmit={submit}><label>Email or mobile number<input value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" placeholder="you@yourbusiness.com" required/></label><label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" placeholder="Enter your password" required/></label>{error && <div className="alert alert-error" role="alert">{error}</div>}{accountType === 'TENANT' && <button type="button" className="button button-secondary button-wide" onClick={() => window.location.assign(`${API_BASE_URL}/oauth2/authorization/google`)}>Continue with Google ↗</button>}{accountType === 'TENANT' && <p className="auth-switch"><button type="button" onClick={() => navigate('/forgot-password')}>Forgot password?</button></p>}<Button type="submit" disabled={loading} className="button-wide">{loading ? 'Signing you in…' : 'Sign in to workspace'} <Icon>↗</Icon></Button>{accountType === 'TENANT' && <p className="auth-switch">New to Byawasaya? <button type="button" onClick={() => navigate('/register')}>Apply for a store</button></p>}<p className="auth-switch">{accountType === 'PLATFORM' ? 'Tenant account?' : 'Platform administrator?'} <button type="button" onClick={() => navigate(accountType === 'PLATFORM' ? '/tenant/login' : '/admin/login')}>{accountType === 'PLATFORM' ? 'Tenant sign in' : 'Platform sign in'}</button></p>{accountType === 'TENANT' && <><div className="auth-divider"><span>DEMO</span></div><button type="button" className="demo-signin" onClick={() => { setUsername('demo@byawasaya.local'); setPassword('DemoStore123!'); setError('Demo account filled in. Sign in to continue.') }}>Fill demo store credentials <span>↗</span></button></>}</form>}
    <p className="auth-legal">By signing in, you agree to use this workspace responsibly. Online payments are available only when configured and integrated for a store.</p>
  </AuthLayout>
}

function Register() {
  const draftStorageKey = 'byawasaya-store-application-draft-token'
  const [draftToken] = useState(() => {
    const saved = window.localStorage.getItem(draftStorageKey)
    if (saved) return saved
    const bytes = new Uint8Array(32)
    window.crypto.getRandomValues(bytes)
    return Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  })
  const [step, setStep] = useState(1)
  const [storeName, setStoreName] = useState('')
  const [phone, setPhone] = useState('')
  const [slug, setSlug] = useState('')
  const [slugTouched, setSlugTouched] = useState(false)
  const [ownerName, setOwnerName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [citizenshipNumber, setCitizenshipNumber] = useState('')
  const [businessType, setBusinessType] = useState('RETAIL')
  const [panVatNumber, setPanVatNumber] = useState('')
  const [registrationNumber, setRegistrationNumber] = useState('')
  const [registrationAuthority, setRegistrationAuthority] = useState('')
  const [registeredAddress, setRegisteredAddress] = useState('')
  const [province, setProvince] = useState('Koshi')
  const [district, setDistrict] = useState('')
  const [municipality, setMunicipality] = useState('')
  const [selectedPlanCode, setSelectedPlanCode] = useState('STARTER')
  const [promoCode, setPromoCode] = useState('')
  const [plans, setPlans] = useState<PlanOption[]>([])
  const [files, setFiles] = useState<Record<string, File | null>>({})
  const [app, setApp] = useState<TenantApplication | null>(null)
  const [hasSavedLead, setHasSavedLead] = useState(() => Boolean(window.localStorage.getItem(draftStorageKey)))
  const [restoring, setRestoring] = useState(true)
  const [loading, setLoading] = useState(false)
  const [busyDocument, setBusyDocument] = useState<string | null>(null)
  const [draftSaving, setDraftSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  useEffect(() => {
    api<PlanOption[]>('/api/plans')
      .then(setPlans)
      .catch(() => setPlans([
        { id: 'starter', code: 'STARTER', name: 'Starter', monthlyPriceNpr: 2500, annualPriceNpr: 25000, trialDays: 14, maxProducts: 100, maxStaff: 2, published: true },
        { id: 'plus', code: 'PLUS', name: 'Plus', monthlyPriceNpr: 3500, annualPriceNpr: 35000, trialDays: 14, maxProducts: 500, maxStaff: 5, published: true },
        { id: 'pro', code: 'PRO', name: 'Pro', monthlyPriceNpr: 5000, annualPriceNpr: 50000, trialDays: 14, maxProducts: 5000, maxStaff: 20, published: true },
      ]))
  }, [])

  useEffect(() => {
    let mounted = true
    async function restore() {
      try {
        const existing = await api<TenantApplication>('/api/tenant-applications/me')
        if (mounted) {
          setApp(existing)
          setSelectedPlanCode(existing.selectedPlanCode)
          setStep(3)
          setSuccess('Your saved application is ready. Continue with document uploads below.')
          window.localStorage.removeItem(draftStorageKey)
          setHasSavedLead(false)
          setRestoring(false)
          return
        }
      } catch {
        // A new applicant has no authenticated application yet.
      }
      if (window.localStorage.getItem(draftStorageKey)) {
        try {
          const saved = await api<{storeName: string; slug: string; phone: string; selectedPlanCode: string}>('/api/tenant-applications/draft/restore', null, {
            method: 'POST',
            body: JSON.stringify({ draftToken }),
          })
          if (mounted) {
            setStoreName(saved.storeName)
            setSlug(saved.slug)
            setPhone(saved.phone)
            setSelectedPlanCode(saved.selectedPlanCode)
            setSuccess('Your store details were restored from the saved draft.')
          }
        } catch {
          window.localStorage.removeItem(draftStorageKey)
          if (mounted) setHasSavedLead(false)
        }
      }
      if (mounted) setRestoring(false)
    }
    void restore()
    return () => { mounted = false }
  }, [draftToken])

  async function saveBasicDraft() {
    const normalizedPhone = phone.trim().replace(/[\s()-]/g, '')
    if (!storeName.trim() || !/^[+]?[0-9]{7,15}$/.test(normalizedPhone) || !/^[a-zA-Z0-9][a-zA-Z0-9-]{2,59}$/.test(slug)) {
      throw new Error('Enter the store name, a valid phone number, and a store URL before continuing.')
    }
    await api('/api/tenant-applications/draft', null, {
      method: 'POST',
      body: JSON.stringify({ draftToken, storeName: storeName.trim(), phone: normalizedPhone, slug, selectedPlanCode }),
    })
    window.localStorage.setItem(draftStorageKey, draftToken)
    setHasSavedLead(true)
  }

  useEffect(() => {
    if (restoring || app || !storeName.trim() || !/^[+]?[0-9\s()-]{7,22}$/.test(phone.trim()) || !/^[a-zA-Z0-9][a-zA-Z0-9-]{2,59}$/.test(slug)) return
    const timer = window.setTimeout(() => {
      setDraftSaving(true)
      void api('/api/tenant-applications/draft', null, {
        method: 'POST',
        body: JSON.stringify({ draftToken, storeName: storeName.trim(), phone: phone.trim(), slug, selectedPlanCode }),
      }).then(() => {
        window.localStorage.setItem(draftStorageKey, draftToken)
        setHasSavedLead(true)
      }).catch(() => {
        // Keep the form usable while offline; the next change or Continue retries the save.
      }).finally(() => setDraftSaving(false))
    }, 700)
    return () => window.clearTimeout(timer)
  }, [app, draftToken, phone, restoring, selectedPlanCode, slug, storeName])

  async function continueFromStore(e: FormEvent) {
    e.preventDefault()
    setLoading(true)
    setError('')
    setSuccess('')
    try {
      await saveBasicDraft()
      setStep(2)
      setSuccess('Store details saved as a draft. Add the owner and business information to continue.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save the store draft.')
    } finally {
      setLoading(false)
    }
  }

  async function createApplication(e: FormEvent) {
    e.preventDefault()
    setLoading(true)
    setError('')
    setSuccess('')
    try {
      await api('/api/tenant-applications/register', null, {
        method: 'POST',
        body: JSON.stringify({
          storeName, ownerName, email, phone, slug, password, citizenshipNumber, businessType,
          panVatNumber, registrationNumber, registrationAuthority, registeredAddress, province,
          district, municipality, selectedPlanCode, selectedTemplateKey: 'classic',
          promoCode: promoCode.trim() || null, draftToken,
        }),
      })
      const application = await api<TenantApplication>('/api/tenant-applications/me')
      setApp(application)
      setStep(3)
      setSuccess(`Your application draft is created. Upload the required documents below. We also sent an email verification link to ${email}.`)
      window.localStorage.removeItem(draftStorageKey)
      setHasSavedLead(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create your application. Your store details remain saved as a draft.')
    } finally {
      setLoading(false)
    }
  }

  async function refreshApplication() {
    setLoading(true)
    setError('')
    try {
      const application = await api<TenantApplication>('/api/tenant-applications/me')
      setApp(application)
      setSelectedPlanCode(application.selectedPlanCode)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not refresh your application.')
    } finally {
      setLoading(false)
    }
  }

  const requiredDocuments = [
    ['CITIZENSHIP_FRONT', 'Citizenship front'],
    ['CITIZENSHIP_BACK', 'Citizenship back'],
    ['OWNER_PHOTO', 'Owner photograph'],
    ['REGISTRATION_CERTIFICATE', 'Government registration certificate'],
    ['PAN_VAT_CERTIFICATE', 'PAN/VAT certificate'],
  ] as const
  const latestDocument = (type: string) => app?.documents
    .filter(document => document.documentType === type && document.verificationStatus !== 'SUPERSEDED')
    .sort((a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime())[0]
  const missingDocuments = requiredDocuments.filter(([type]) => !latestDocument(type))

  async function uploadDocument(type: string) {
    const file = files[type]
    if (!file) return
    setBusyDocument(type)
    setError('')
    setSuccess('')
    try {
      const data = new FormData()
      data.append('documentType', type)
      data.append('file', file)
      await api('/api/tenant-applications/me/documents', null, { method: 'POST', body: data })
      setFiles(old => ({ ...old, [type]: null }))
      await refreshApplication()
      setSuccess(`${pretty(type)} uploaded and saved to your draft.`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed. Your application draft is safe; choose the file again to retry.')
    } finally {
      setBusyDocument(null)
    }
  }

  async function submitApplication() {
    setLoading(true)
    setError('')
    setSuccess('')
    try {
      const submitted = await api<TenantApplication>('/api/tenant-applications/me/submit', null, { method: 'POST' })
      setApp(submitted)
      setSuccess('Application submitted for review. You can sign in later to check its status.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not submit your application.')
    } finally {
      setLoading(false)
    }
  }

  const fileField = (type: string, label: string) => {
    const document = latestDocument(type)
    return (
      <article className="register-document" key={type}>
        <div className="register-document-copy">
          <strong>{label}</strong>
          <span>{document ? document.originalFilename : 'Required · JPEG, PNG or PDF · up to 8 MB'}</span>
          {document && <Pill tone={document.verificationStatus === 'VERIFIED' ? 'green' : document.verificationStatus === 'REJECTED' ? 'red' : 'amber'}>{pretty(document.verificationStatus)}</Pill>}
        </div>
        <label className="register-file-picker">{files[type]?.name ?? (document ? 'Replace file' : 'Choose file')}
          <input type="file" accept={['OWNER_PHOTO', 'CITIZENSHIP_FRONT', 'CITIZENSHIP_BACK'].includes(type) ? 'image/jpeg,image/png' : 'image/jpeg,image/png,application/pdf'} onChange={event => setFiles(old => ({ ...old, [type]: event.target.files?.[0] ?? null }))}/>
        </label>
        {files[type] && <Button variant="secondary" disabled={busyDocument !== null || loading} onClick={() => void uploadDocument(type)}>{busyDocument === type ? 'Uploading…' : 'Upload'}</Button>}
      </article>
    )
  }

  const showOauthNotice = new URLSearchParams(window.location.search).get('oauth') === 'unlinked'
  const submittingAllowed = Boolean(app?.emailVerified && missingDocuments.length === 0 && ['DRAFT', 'NEEDS_CORRECTION'].includes(app.status))

  return (
    <AuthLayout wide title="Apply to open your store." subtitle="Save your store details first, then add the owner and upload documents in separate steps." sideTitle="A safer start for your business." sideText="Your store information is saved as a draft as you go. Our team can follow up if you need help finishing your application.">
      {showOauthNotice && <div className="alert alert-info">Your Google identity is not linked yet. Create your account with the same verified email, verify it, then use Google sign-in to link the account securely.</div>}
      {restoring ? <div className="register-loading">Restoring your saved application…</div> : (
        <div className="register-flow">
          <div className="register-stepper" aria-label={`Step ${step} of 3`}>
            {['Store basics', 'Owner details', 'Documents'].map((label, index) => {
              const number = index + 1
              return <div className={`register-step ${step === number ? 'register-step-active' : ''} ${step > number ? 'register-step-complete' : ''}`} key={label}><span>{step > number ? '✓' : `0${number}`}</span><b>{label}</b></div>
            })}
          </div>
          {error && <div className="alert alert-error" role="alert">{error}</div>}
          {success && <div className="alert alert-success" role="status">{success}</div>}
          {step === 1 && (
            <form className="auth-form register-form" onSubmit={continueFromStore}>
              <div className="register-panel-heading"><span>STEP 01</span><h3>Tell us about your store</h3><p>We’ll save your store name and phone number so our team can follow up if you pause here.</p></div>
              <label>Store / business name<input value={storeName} onChange={event => { setStoreName(event.target.value); if (!slugTouched) setSlug(slugify(event.target.value)) }} required maxLength={120} autoFocus/></label>
              <label>Phone number<input type="tel" value={phone} onChange={event => setPhone(event.target.value)} required maxLength={30} autoComplete="tel" placeholder="e.g. 9800000000"/><small className="field-help">Use a number where we can reach you.</small></label>
              <label>Store URL<div className="slug-input"><span>byawasaya.com/store/</span><input value={slug} onChange={event => { setSlugTouched(true); setSlug(slugify(event.target.value)) }} required minLength={3} maxLength={60} pattern="[a-zA-Z0-9][a-zA-Z0-9-]{2,59}"/></div></label>
              <div className="register-draft-status" aria-live="polite"><span className={draftSaving ? 'register-save-dot register-save-pending' : 'register-save-dot'} />{draftSaving ? 'Saving your draft…' : hasSavedLead ? 'Your store details are saved as a draft.' : 'Your details save automatically as you type.'}</div>
              <Button type="submit" disabled={loading || draftSaving} className="button-wide">{loading ? 'Saving…' : 'Save and continue'} <Icon>↗</Icon></Button>
              <p className="auth-switch">Already have an account? <button type="button" onClick={() => navigate('/tenant/login')}>Sign in</button></p>
            </form>
          )}
          {step === 2 && (
            <form className="auth-form register-form" onSubmit={createApplication}>
              <div className="register-panel-heading"><span>STEP 02 · {storeName}</span><h3>Owner and business details</h3><p>Your contact phone is saved. Add the owner account and registration information.</p></div>
              <label>Full legal name<input value={ownerName} onChange={event => setOwnerName(event.target.value)} required maxLength={120} autoComplete="name"/></label>
              <div className="form-two-col"><label>Email<input type="email" value={email} onChange={event => setEmail(event.target.value)} required autoComplete="email"/></label><label>Business type<select value={businessType} onChange={event => setBusinessType(event.target.value)}><option value="RETAIL">Retail</option><option value="WHOLESALE">Wholesale</option><option value="BOTH">Retail and wholesale</option></select></label></div>
              <label>Create password<input type="password" value={password} onChange={event => setPassword(event.target.value)} minLength={12} maxLength={72} required autoComplete="new-password"/><small className="field-help">Use at least 12 characters.</small></label>
              <div className="form-two-col"><label>Citizenship number<input value={citizenshipNumber} onChange={event => setCitizenshipNumber(event.target.value)} required maxLength={100}/></label><label>PAN/VAT number<input value={panVatNumber} onChange={event => setPanVatNumber(event.target.value)} required maxLength={100}/></label></div>
              <div className="form-two-col"><label>Government registration number<input value={registrationNumber} onChange={event => setRegistrationNumber(event.target.value)} required maxLength={120}/></label><label>Registration authority<input value={registrationAuthority} onChange={event => setRegistrationAuthority(event.target.value)} required maxLength={180} placeholder="e.g. Office of Company Registrar"/></label></div>
              <label>Registered business address<input value={registeredAddress} onChange={event => setRegisteredAddress(event.target.value)} required maxLength={500}/></label>
              <div className="form-two-col"><label>Province<input value={province} onChange={event => setProvince(event.target.value)} required maxLength={100}/></label><label>District<input value={district} onChange={event => setDistrict(event.target.value)} required maxLength={100}/></label></div>
              <div className="form-two-col"><label>Municipality / rural municipality<input value={municipality} onChange={event => setMunicipality(event.target.value)} required maxLength={120}/></label><label>Plan<select value={selectedPlanCode} onChange={event => setSelectedPlanCode(event.target.value)}>{plans.map(plan => <option key={plan.code} value={plan.code}>{plan.name} · {money(plan.monthlyPriceNpr)}/month · {plan.trialDays}-day trial</option>)}</select></label></div>
              <label>Promo code (optional)<input value={promoCode} onChange={event => setPromoCode(event.target.value.toUpperCase())} maxLength={60}/></label>
              <div className="template-choice"><div className="template-preview"><div/><i/><i/><i/></div><div><b>Classic storefront</b><small>Responsive template · Included in every plan</small></div><Pill tone="green">Selected</Pill></div>
              <p className="auth-legal">By continuing, you confirm the information is accurate and authorize Byawasaya to verify the business.</p>
              <div className="register-navigation"><Button type="button" variant="secondary" disabled={loading} onClick={() => { setStep(1); setError(''); setSuccess('') }}>Back</Button><Button type="submit" disabled={loading || draftSaving}>{loading ? 'Saving application…' : 'Create application and continue'} <Icon>↗</Icon></Button></div>
            </form>
          )}
          {step === 3 && app && (
            <div className="register-form register-document-step">
              <div className="register-panel-heading"><span>STEP 03 · {app.storeName}</span><h3>Upload your documents</h3><p>Each file saves separately. If an upload fails, choose that file again to retry; your application and other uploads stay saved.</p></div>
              {!app.emailVerified && <div className="register-verification-note"><strong>Verify your email to submit</strong><span>Use the verification link sent to {app.ownerEmail || 'your email address'}. You can upload documents while you wait, then refresh this page.</span><Button variant="secondary" disabled={loading} onClick={() => void refreshApplication()}>Refresh email status</Button></div>}
              <div className="register-document-list">{requiredDocuments.map(([type, label]) => fileField(type, label))}</div>
              {app.applicantNotes && <div className="register-verification-note"><strong>Review update</strong><span>{app.applicantNotes}</span></div>}
              {app.publicReviewNotes && <div className="register-verification-note"><strong>Platform review note</strong><span>{app.publicReviewNotes}</span></div>}
              {app.status === 'SUBMITTED' || app.status === 'UNDER_REVIEW' ? <div className="register-submitted-state"><Pill tone="green">{pretty(app.status)}</Pill><strong>Your application is with our review team.</strong><span>We’ll contact you at {app.phone} if we need anything else.</span></div> : (
                <div className="register-submit-box"><div><strong>{missingDocuments.length ? `${missingDocuments.length} required document${missingDocuments.length === 1 ? '' : 's'} remaining` : 'All required documents uploaded'}</strong><span>{app.emailVerified ? 'Submit when you are ready for platform review.' : 'Verify your email, then refresh to enable submission.'}</span></div><Button disabled={loading || !submittingAllowed} onClick={() => void submitApplication()}>{loading ? 'Submitting…' : 'Submit application for review'} <Icon>↗</Icon></Button></div>
              )}
            </div>
          )}
        </div>
      )}
    </AuthLayout>
  )
}

function VerifyEmailPage() {
  const [token] = useState(() => new URLSearchParams(window.location.search).get('token'))
  const [message, setMessage] = useState(token ? 'Verifying your email…' : 'This verification link is missing its token.')
  const [error, setError] = useState(!token)
  useEffect(() => { if (!token) return; api<{message:string}>('/api/auth/email/verification/confirm', null, { method:'POST', body:JSON.stringify({token}) }).then(r => setMessage(r.message)).catch(e => { setMessage(e instanceof Error ? e.message : 'Verification failed.'); setError(true) }) }, [token])
  return <AuthLayout title="Verify your email." subtitle="Email verification protects your account and is required before your application can be submitted." sideTitle="One small step." sideText="Once verified, return to your onboarding workspace to submit your store application."><div className={`alert ${error ? 'alert-error' : 'alert-success'}`} role="status">{message}</div><Button className="button-wide" onClick={() => window.location.assign('/tenant/onboarding')}>Continue to onboarding ↗</Button></AuthLayout>
}

function ForgotPasswordPage() {
  const [email, setEmail] = useState(''); const [message, setMessage] = useState(''); const [error, setError] = useState(''); const [loading, setLoading] = useState(false)
  async function submit(e:FormEvent){e.preventDefault();setLoading(true);setError('');setMessage('');try{const r=await api<{message:string}>('/api/auth/password/forgot',null,{method:'POST',body:JSON.stringify({email})});setMessage(r.message)}catch(e){setError(e instanceof Error?e.message:'Could not submit request.')}finally{setLoading(false)}}
  return <AuthLayout title="Reset your password." subtitle="We'll send a one-time reset link if the email belongs to an account." sideTitle="Back to business, securely." sideText="Password-reset links expire and can only be used once."><form className="auth-form" onSubmit={submit}><label>Account email<input type="email" value={email} onChange={e=>setEmail(e.target.value)} required autoComplete="email"/></label>{error&&<div className="alert alert-error">{error}</div>}{message&&<div className="alert alert-success">{message}</div>}<Button type="submit" className="button-wide" disabled={loading}>{loading?'Sending…':'Send reset link'}</Button><p className="auth-switch"><button type="button" onClick={()=>navigate('/tenant/login')}>Back to sign in</button></p></form></AuthLayout>
}

function ResetPasswordPage() {
  const [password,setPassword]=useState('');const [message,setMessage]=useState('');const [error,setError]=useState('');const [loading,setLoading]=useState(false)
  async function submit(e:FormEvent){e.preventDefault();setLoading(true);setError('');try{const token=new URLSearchParams(window.location.search).get('token');if(!token)throw new Error('Reset link is missing its token.');const r=await api<{message:string}>('/api/auth/password/reset',null,{method:'POST',body:JSON.stringify({token,password})});setMessage(r.message)}catch(e){setError(e instanceof Error?e.message:'Could not reset password.')}finally{setLoading(false)}}
  return <AuthLayout title="Choose a new password." subtitle="Use a strong password you do not use elsewhere." sideTitle="Secure your account." sideText="After a reset, existing sessions are revoked and you must sign in again."><form className="auth-form" onSubmit={submit}><label>New password<input type="password" value={password} onChange={e=>setPassword(e.target.value)} required minLength={12} maxLength={72} autoComplete="new-password"/></label>{error&&<div className="alert alert-error">{error}</div>}{message&&<div className="alert alert-success">{message}</div>}<Button type="submit" className="button-wide" disabled={loading}>{loading?'Updating…':'Reset password'}</Button>{message&&<Button type="button" variant="secondary" className="button-wide" onClick={()=>navigate('/tenant/login')}>Go to sign in</Button>}</form></AuthLayout>
}

function OnboardingWorkspace({ user, onLogout }: { user: AuthUser; onLogout: () => void }) {
  const [app,setApp]=useState<TenantApplication|null>(null);const [plans,setPlans]=useState<PlanOption[]>([]);const [error,setError]=useState('');const [success,setSuccess]=useState('');const [loading,setLoading]=useState(true);const [busy,setBusy]=useState(false);const [files,setFiles]=useState<Record<string,File|null>>({});const [plan,setPlan]=useState('STARTER');const [promo,setPromo]=useState('')
  const requiredDocumentTypes=['CITIZENSHIP_FRONT','CITIZENSHIP_BACK','OWNER_PHOTO','REGISTRATION_CERTIFICATE','PAN_VAT_CERTIFICATE']
  const missingRequiredDocuments=requiredDocumentTypes.filter(type=>!app?.documents.some(doc=>doc.documentType===type&&doc.verificationStatus!=='SUPERSEDED'))
  async function refresh(){try{const [a,p]=await Promise.all([api<TenantApplication>('/api/tenant-applications/me'),api<PlanOption[]>('/api/plans')]);setApp(a);setPlan(a.selectedPlanCode);setPromo(a.promoCode??'');setPlans(p)}catch(e){setError(e instanceof Error?e.message:'Could not load application.')}finally{setLoading(false)}}
 // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(()=>{void refresh()},[])
  async function upload(type:string){const file=files[type];if(!file)return;setBusy(true);setError('');try{const data=new FormData();data.append('documentType',type);data.append('file',file);await api('/api/tenant-applications/me/documents',null,{method:'POST',body:data});setSuccess('Document uploaded.');setFiles(old=>({...old,[type]:null}));await refresh()}catch(e){setError(e instanceof Error?e.message:'Upload failed.')}finally{setBusy(false)}}
  async function savePlan(){if(!app)return;setBusy(true);setError('');try{await api('/api/tenant-applications/me',null,{method:'PATCH',body:JSON.stringify({citizenshipNumber:app.citizenshipNumber,businessType:app.businessType,panVatNumber:app.panVatNumber,registrationNumber:app.registrationNumber,registrationAuthority:app.registrationAuthority,registeredAddress:app.registeredAddress,province:app.province,district:app.district,municipality:app.municipality,selectedPlanCode:plan,selectedTemplateKey:'classic',promoCode:promo||null,applicantNotes:app.applicantNotes||''})});setSuccess('Plan selection saved.');await refresh()}catch(e){setError(e instanceof Error?e.message:'Could not save changes.')}finally{setBusy(false)}}
  async function submit(){if(!app)return;setBusy(true);setError('');setSuccess('');try{await api('/api/tenant-applications/me',null,{method:'PATCH',body:JSON.stringify({citizenshipNumber:app.citizenshipNumber,businessType:app.businessType,panVatNumber:app.panVatNumber,registrationNumber:app.registrationNumber,registrationAuthority:app.registrationAuthority,registeredAddress:app.registeredAddress,province:app.province,district:app.district,municipality:app.municipality,selectedPlanCode:plan,selectedTemplateKey:'classic',promoCode:promo||null,applicantNotes:app.applicantNotes||''})});await api('/api/tenant-applications/me/submit',null,{method:'POST'});setSuccess('Application submitted for review.');await refresh()}catch(e){setError(e instanceof Error?e.message:'Could not submit application.')}finally{setBusy(false)}}
  async function resend(){setBusy(true);try{const r=await api<{message:string}>('/api/auth/email/verification/resend',null,{method:'POST'});setSuccess(r.message)}catch(e){setError(e instanceof Error?e.message:'Could not send verification email.')}finally{setBusy(false)}}
  const uploadField=(type:string,label:string)=>{const doc=app?.documents.filter(d=>d.documentType===type).sort((a,b)=>new Date(b.uploadedAt).getTime()-new Date(a.uploadedAt).getTime())[0];return <div className="onboarding-document" key={type}><div><b>{label} <span aria-hidden="true">*</span></b><small>{doc?`${doc.originalFilename} · ${pretty(doc.verificationStatus)}`:'Not uploaded'}</small>{doc?.reviewerNote&&<small>{doc.reviewerNote}</small>}</div><label className="file-compact">{doc?'Replace file':'Choose file'}<input type="file" disabled={!app||!(['DRAFT','NEEDS_CORRECTION'].includes(app.status))} accept={['OWNER_PHOTO','CITIZENSHIP_FRONT','CITIZENSHIP_BACK'].includes(type)?'image/jpeg,image/png':'image/jpeg,image/png,application/pdf'} onChange={e=>setFiles(old=>({...old,[type]:e.target.files?.[0]??null}))}/></label>{files[type]&&<Button variant="secondary" disabled={busy} onClick={()=>void upload(type)}>Upload</Button>}</div>}
  if(loading)return <div className="session-checking">Loading your application…</div>
  return <DashboardFrame user={user} title="Store application" eyebrow="TENANT ONBOARDING" active="onboarding" onLogout={onLogout}><section className="surface-card page-table-card"><div className="surface-heading"><div><h2>{app?.storeName??'Your application'}</h2><p>Store ID · {app?.storeCode} · {app?.slug}</p></div>{app?.status==='APPROVED'&&<Button variant="secondary" onClick={()=>navigate('/tenant/billing')}>Subscription invoices</Button>}<Pill tone={app?.status==='APPROVED'?'green':app?.status==='REJECTED'?'red':'amber'}>{pretty(app?.status??'DRAFT')}</Pill></div>{error&&<div className="alert alert-error">{error}</div>}{success&&<div className="alert alert-success">{success}</div>}{!app?.emailVerified&&<div className="notice-note"><span>!</span><p>Your email is not verified. Check your inbox for the verification link. <button className="subtle-link" onClick={()=>void resend()}>Resend verification email</button></p></div>}{app&&['DRAFT','NEEDS_CORRECTION'].includes(app.status)&&<div className="notice-note"><span>i</span><p>Draft applications are not listed under Submitted. Verify your email and upload all five required documents to submit. {missingRequiredDocuments.length>0?`${missingRequiredDocuments.length} required document(s) still missing.`:'All required documents are uploaded.'}</p></div>}{app?.publicReviewNotes&&<div className="notice-note"><span>i</span><p><b>Platform review note:</b> {app.publicReviewNotes}</p></div>}{app?.status==='APPROVED'&&user.storeStatus==='ACTIVE'?<div className="alert alert-success">Your store has been approved and your trial is active. <Button onClick={()=>navigate('/tenant/dashboard')}>Open workspace</Button></div>:app?.status==='APPROVED'&&user.storeStatus==='TRIAL_EXPIRED'?<div className="alert alert-error">Your trial has ended. Contact platform administration to renew your subscription before the storefront can operate. Use Subscription invoices to review your account.</div>:<><h3>Owner and business information</h3><div className="form-grid"><label>Citizenship number<input value={app?.citizenshipNumber??''} onChange={e=>setApp(old=>old?({...old,citizenshipNumber:e.target.value}):old)} disabled={!['DRAFT','NEEDS_CORRECTION'].includes(app?.status??'')} required/></label><label>Business type<select value={app?.businessType??'RETAIL'} onChange={e=>setApp(old=>old?({...old,businessType:e.target.value}):old)} disabled={!['DRAFT','NEEDS_CORRECTION'].includes(app?.status??'')}><option value="RETAIL">Retail</option><option value="WHOLESALE">Wholesale</option><option value="BOTH">Retail and wholesale</option></select></label><label>PAN/VAT number<input value={app?.panVatNumber??''} onChange={e=>setApp(old=>old?({...old,panVatNumber:e.target.value}):old)} disabled={!['DRAFT','NEEDS_CORRECTION'].includes(app?.status??'')} required/></label><label>Government registration number<input value={app?.registrationNumber??''} onChange={e=>setApp(old=>old?({...old,registrationNumber:e.target.value}):old)} disabled={!['DRAFT','NEEDS_CORRECTION'].includes(app?.status??'')} required/></label><label>Registration authority<input value={app?.registrationAuthority??''} onChange={e=>setApp(old=>old?({...old,registrationAuthority:e.target.value}):old)} disabled={!['DRAFT','NEEDS_CORRECTION'].includes(app?.status??'')} required/></label><label>Registered address<input value={app?.registeredAddress??''} onChange={e=>setApp(old=>old?({...old,registeredAddress:e.target.value}):old)} disabled={!['DRAFT','NEEDS_CORRECTION'].includes(app?.status??'')} required/></label><label>Province<input value={app?.province??''} onChange={e=>setApp(old=>old?({...old,province:e.target.value}):old)} disabled={!['DRAFT','NEEDS_CORRECTION'].includes(app?.status??'')} required/></label><label>District<input value={app?.district??''} onChange={e=>setApp(old=>old?({...old,district:e.target.value}):old)} disabled={!['DRAFT','NEEDS_CORRECTION'].includes(app?.status??'')} required/></label><label>Municipality<input value={app?.municipality??''} onChange={e=>setApp(old=>old?({...old,municipality:e.target.value}):old)} disabled={!['DRAFT','NEEDS_CORRECTION'].includes(app?.status??'')} required/></label></div><h3>Identity and registration documents</h3>{uploadField('CITIZENSHIP_FRONT','Citizenship front')}{uploadField('CITIZENSHIP_BACK','Citizenship back')}{uploadField('OWNER_PHOTO','Owner photograph')}{uploadField('REGISTRATION_CERTIFICATE','Government registration certificate')}{uploadField('PAN_VAT_CERTIFICATE','PAN/VAT certificate')}<h3>Storefront and plan</h3><div className="form-grid"><label>Plan<select value={plan} onChange={e=>setPlan(e.target.value)} disabled={!['DRAFT','NEEDS_CORRECTION'].includes(app?.status??'')} >{plans.map(p=><option key={p.code} value={p.code}>{p.name} · {money(p.monthlyPriceNpr)}/month</option>)}</select></label><label>Promo code<input value={promo} onChange={e=>setPromo(e.target.value.toUpperCase())} disabled={!['DRAFT','NEEDS_CORRECTION'].includes(app?.status??'')}/></label></div>{['DRAFT','NEEDS_CORRECTION'].includes(app?.status??'')&&<div className="product-form-actions"><Button variant="secondary" disabled={busy} onClick={()=>void savePlan()}>Save application details</Button><Button disabled={busy||!app?.emailVerified||missingRequiredDocuments.length>0||Object.values(files).some(Boolean)} onClick={()=>void submit()}>Submit for verification ↗</Button></div>}{app?.status==='SUBMITTED'||app?.status==='UNDER_REVIEW'?<p>Your application is in the platform review queue. You will see notes here if more information is required.</p>:null}</>}</section></DashboardFrame>
}

function DashboardFrame({ user, title, eyebrow, children, active, onTab, onLogout }: { user: AuthUser; title: string; eyebrow: string; children: ReactNode; active?: string; onTab?: (tab: string) => void; onLogout: () => void }) {
  const adminLinks = [{ id: 'overview', label: 'Overview', icon: '▤' }, { id: 'platform-users', label: 'Platform users', icon: '♙' }, { id: 'users', label: 'Tenant users', icon: '♧' }, { id: 'applications', label: 'Applications', icon: '✓' }, { id: 'businesses', label: 'Tenants & stores', icon: '▦' }, { id: 'plans', label: 'Plans & pricing', icon: 'रु' }, { id: 'subscriptions', label: 'Subscriptions', icon: '↻' }, { id: 'billing', label: 'Billing', icon: '▤' }, { id: 'promotions', label: 'Promotions', icon: '%' }, { id: 'audit', label: 'Audit logs', icon: '≡' }, { id: 'security', label: 'Security', icon: '⌑' }, { id: 'orders', label: 'Order activity', icon: '↗' }]
  const merchantLinks = [{ id: 'overview', label: 'Overview', icon: '◫' }, { id: 'products', label: 'Products', icon: '▦' }, { id: 'orders', label: 'Orders', icon: '↗' }, { id: 'billing', label: 'Plan & billing', icon: 'रु' }, { id: 'staff', label: 'Staff & access', icon: '♙' }, { id: 'store', label: 'Store settings', icon: '⚙' }]
  const links = isPlatformRole(user.role) ? adminLinks.filter(link => user.role === 'SUPERADMIN' || ['overview', 'users', 'applications', 'businesses', 'orders', 'security'].includes(link.id)) : user.role === 'STORE_STAFF' ? merchantLinks.filter(link => ['overview','orders'].includes(link.id)) : merchantLinks
  return <div className={`workspace ${isPlatformRole(user.role) ? 'workspace-admin' : 'workspace-merchant'}`}><aside className="workspace-sidebar"><div className="workspace-brand"><Logo/><span className="workspace-brand-label">{isPlatformRole(user.role) ? 'PLATFORM ADMIN' : 'MERCHANT WORKSPACE'}</span></div><div className="workspace-switcher"><span className="switcher-icon">{isPlatformRole(user.role) ? '⌘' : '▦'}</span><span><small>WORKSPACE</small><b>{isPlatformRole(user.role) ? 'Byawasaya Platform' : 'Your store'}</b></span><span className="switcher-caret">⌄</span></div><span className="sidebar-section-label">WORKSPACE</span><nav className="workspace-nav">{links.map(link => <button key={link.id} className={active === link.id ? 'workspace-nav-active' : ''} onClick={() => onTab?.(link.id)}><span className="nav-icon">{link.icon}</span><span>{link.label}</span>{link.id === 'orders' && <span className="nav-arrow">›</span>}</button>)}</nav><div className="workspace-sidebar-bottom"><div className="sidebar-tip"><span>✦</span><b>{isPlatformRole(user.role) ? 'Platform administration' : 'Your daily workspace'}</b><p>{isPlatformRole(user.role) ? 'Review accounts, applications, plans and business status.' : 'Simple tools for getting your work done.'}</p></div><button className="sidebar-user" onClick={onLogout}><span className="user-avatar">{initials(user.username.includes('@') ? user.username.split('@')[0] : user.username)}</span><span className="user-identity"><b>{user.username}</b><small>{user.role === 'SUPERADMIN' ? 'Superadmin' : user.role === 'STORE_OWNER' ? 'Store owner' : user.role === 'STORE_STAFF' ? 'Store staff' : 'Platform staff'}</small></span><span className="user-menu">↪</span></button></div></aside><main className="workspace-main"><header className="workspace-topbar"><div className="topbar-breadcrumb"><span>Byawasaya</span><b>/</b><strong>{title}</strong></div><div className="topbar-right"><span className="system-status"><i/> Platform workspace</span><span className="topbar-help">Administrator console</span><button className="topbar-avatar" title="Sign out" onClick={onLogout}>{initials(user.username)}</button></div></header><div className="workspace-content"><div className="workspace-page-heading"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1></div>{user.role === 'STORE_OWNER' && user.storeSlug && <Button variant="secondary" onClick={() => navigate(`/store/${user.storeSlug}`)}>View storefront <Icon>↗</Icon></Button>}</div>{children}<footer className="workspace-footer"><span>© 2026 Byawasaya</span><span>Built for Nepal's next generation of businesses.</span></footer></div></main></div>
}

function AdminDashboard({ token, user, onLogout }: { token: string; user: AuthUser; onLogout: () => void }) {
  const [overview, setOverview] = useState<AdminOverview | null>(null)
  const [businesses, setBusinesses] = useState<Business[]>([])
  const [platformUsers, setPlatformUsers] = useState<PlatformUser[]>([])
  const [userQuery, setUserQuery] = useState('')
  const [activeTab, setActiveTab] = useState(window.location.pathname === '/admin/security' ? 'security' : 'overview')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  async function refresh() {
    setLoading(true); setError('')
    try { const [dash, list, users] = await Promise.all([api<AdminOverview>('/api/admin/dashboard', token), api<Business[]>('/api/admin/businesses', token), api<PlatformUser[]>('/api/admin/users', token)]); setOverview(dash); setBusinesses(list); setPlatformUsers(users) }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to load platform data.') }
    finally { setLoading(false) }
  }
  // Fetch initial platform data when the authenticated session changes.
  // eslint-disable-next-line react-hooks/set-state-in-effect, react-hooks/exhaustive-deps
  useEffect(() => { void refresh() }, [token])

  async function setStatus(id: string, status: string) {
    setError('')
    try { await api(`/api/admin/businesses/${id}/status`, token, { method: 'PATCH', body: JSON.stringify({ status }) }); await refresh() }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not update status.') }
  }
  async function setUserEnabled(id: string, enabled: boolean) {
    setError('')
    try { await api(`/api/admin/users/${id}/enabled`, token, { method: 'PATCH', body: JSON.stringify({ enabled }) }); await refresh() }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not update user access.') }
  }
  const filteredUsers = platformUsers.filter(u => `${u.username} ${u.role} ${u.businessName}`.toLowerCase().includes(userQuery.toLowerCase()))
  return <DashboardFrame user={user} title={activeTab === 'platform-users' ? 'Platform users' : activeTab === 'users' ? 'Tenant users' : activeTab === 'applications' ? 'Store applications' : activeTab === 'businesses' ? 'Tenants & stores' : activeTab === 'plans' ? 'Plans & pricing' : activeTab === 'subscriptions' ? 'Subscriptions' : activeTab === 'billing' ? 'Subscription billing' : activeTab === 'promotions' ? 'Promotions' : activeTab === 'audit' ? 'Audit logs' : activeTab === 'security' ? 'Security settings' : activeTab === 'orders' ? 'Order activity' : 'Platform overview'} eyebrow="PLATFORM ADMINISTRATION" active={activeTab} onTab={tab => { setActiveTab(tab); navigate(tab === 'security' ? '/admin/security' : '/admin/dashboard') }} onLogout={onLogout}>
    {error && <div className="alert alert-error workspace-alert">{error}<button onClick={() => setError('')}>×</button></div>}
    {activeTab === 'overview' && <>
      <div className="welcome-banner"><div><Pill tone="green">PLATFORM AT A GLANCE</Pill><h2>Make commerce <em>work better.</em></h2><p>Monitor stores and customer activity across the Byawasaya platform.</p></div><div className="banner-art"><span className="banner-ring ring-a"/><span className="banner-ring ring-b"/><span className="banner-monogram">b.</span><span className="banner-spark">✳</span></div></div>
      <div className="metric-grid"><Metric label="Total businesses" value={loading ? '—' : overview?.totalBusinesses ?? 0} icon="▦" accent="mint" note="Registered stores"/><Metric label="Active businesses" value={loading ? '—' : overview?.activeBusinesses ?? 0} icon="✓" accent="blue" note="Currently published"/><Metric label="Orders received" value={loading ? '—' : overview?.totalOrders ?? 0} icon="↗" accent="lilac" note="All stores"/><Metric label="Order value" value={loading ? '—' : money(overview?.grossSales ?? 0)} icon="रु" accent="sand" note="Not net revenue"/></div>
      <section className="dashboard-columns"><article className="surface-card"><div className="surface-heading"><div><h2>Recently joined</h2><p>The latest businesses on your platform</p></div><button className="subtle-link" onClick={() => setActiveTab('businesses')}>View all <span>↗</span></button></div><BusinessTable businesses={overview?.recentBusinesses ?? []} loading={loading} onStatus={setStatus}/></article><aside className="surface-card operations-card"><div className="surface-heading"><div><h2>Platform health</h2><p>Core platform capabilities</p></div></div><div className="health-list"><HealthRow name="Store registration" detail="Self-service onboarding"/><HealthRow name="Product catalog" detail="Retail + wholesale pricing"/><HealthRow name="Order workflow" detail="Statuses and customer details"/><HealthRow name="Payment gateway" detail="Integration not enabled" warn/></div><div className="notice-note"><span>i</span><p>Online payments remain disabled until a verified gateway integration is available.</p></div></aside></section>
    </>}
    {activeTab === 'users' && <section className="surface-card page-table-card user-admin-card"><div className="surface-heading"><div><h2>Registered user accounts</h2><p>Review merchant logins, assigned business and access status.</p></div><Pill>{platformUsers.length} accounts</Pill></div><div className="table-toolbar"><label className="table-search"><span>⌕</span><input value={userQuery} onChange={e => setUserQuery(e.target.value)} placeholder="Search username, role or business" aria-label="Search platform users" /></label><span className="table-toolbar-note">{platformUsers.filter(u => u.enabled).length} enabled · {platformUsers.filter(u => !u.enabled).length} disabled</span></div>{loading ? <div className="table-message">Loading user accounts…</div> : filteredUsers.length ? <div className="table-scroll"><table className="data-table user-data-table"><thead><tr><th>ACCOUNT</th><th>ROLE</th><th>BUSINESS</th><th>CREATED</th><th>ACCESS</th><th>ACTION</th></tr></thead><tbody>{filteredUsers.map(u => <tr key={u.id}><td><div className="table-business"><span className="business-avatar">{initials(u.username)}</span><span><b>{u.username}</b><small>Account ID · {u.id.slice(0, 8)}</small></span></div></td><td><Pill tone={u.role === 'SUPERADMIN' ? 'blue' : 'neutral'}>{pretty(u.role)}</Pill></td><td>{u.businessName || '—'}</td><td>{dateLabel(u.createdAt)}</td><td><Pill tone={u.enabled ? 'green' : 'red'}>{u.enabled ? 'Enabled' : 'Disabled'}</Pill></td><td><Button variant={u.enabled ? 'danger' : 'secondary'} disabled={u.username.toLowerCase() === user.username.toLowerCase()} onClick={() => setUserEnabled(u.id, !u.enabled)}>{u.enabled ? 'Disable access' : 'Enable access'}</Button></td></tr>)}</tbody></table></div> : <EmptyState title="No matching accounts" text="Try another username, role or business name."/>}<div className="user-admin-footnote"><strong>Access policy</strong><span>The bootstrap superadmin is configured through server environment variables and is not stored in this list. Disabling a merchant revokes their active platform sessions; admin invitations and password resets require a verified workflow.</span></div></section>}
    {activeTab === 'businesses' && <section className="surface-card page-table-card"><div className="surface-heading"><div><h2>Tenants and stores</h2><p>New stores must pass the application and document-verification workflow before activation.</p></div><Button variant="secondary" onClick={()=>setActiveTab('applications')}>Review applications ↗</Button></div><BusinessTable businesses={businesses} loading={loading} onStatus={setStatus} full/></section>}
    {activeTab === 'orders' && <section className="surface-card page-table-card"><div className="surface-heading"><div><h2>Order activity</h2><p>Order-level customer information stays within each store owner's workspace.</p></div></div><div className="activity-placeholder"><span className="large-stat">{overview?.totalOrders ?? '—'}</span><div><b>Orders across the platform</b><p>Totals include legacy records and provisioned tenant tables.</p></div></div><p className="quiet-footnote">Payment-related revenue is not included here. The current total is the sum of non-cancelled order values.</p></section>}
    {activeTab === 'applications' && <ApplicationsPanel token={token}/>}
    {activeTab === 'platform-users' && <PlatformUsersPanel token={token} user={user}/>}
    {activeTab === 'plans' && <PlansPanel token={token}/>}
    {activeTab === 'subscriptions' && <SubscriptionsPanel token={token}/>}
    {activeTab === 'billing' && <BillingPanel token={token}/>}
    {activeTab === 'promotions' && <PromotionsPanel token={token}/>}
    {activeTab === 'audit' && <AuditPanel token={token}/>}
    {activeTab === 'security' && <PlatformMfaSettings token={token} user={user} onEnabled={() => window.location.reload()} onLogout={onLogout}/>}
  </DashboardFrame>
}
function StaffPanel({ token }: { token: string }) {
 const [staff,setStaff]=useState<Array<Record<string,unknown>>>([]);const [fullName,setFullName]=useState('');const [email,setEmail]=useState('');const [phone,setPhone]=useState('');const [role,setRole]=useState('STAFF');const [error,setError]=useState('');const [message,setMessage]=useState('');const [busy,setBusy]=useState(false)
 async function load(){try{setStaff(await api<Array<Record<string,unknown>>>('/api/merchant/staff',token))}catch(e){setError(e instanceof Error?e.message:'Could not load staff.')}}
 // eslint-disable-next-line react-hooks/set-state-in-effect, react-hooks/exhaustive-deps
 useEffect(()=>{void load()},[])
 async function create(e:FormEvent){e.preventDefault();setBusy(true);setError('');setMessage('');try{const r=await api<{message:string}>('/api/merchant/staff',token,{method:'POST',body:JSON.stringify({fullName,email,phone,role})});setMessage(r.message);setFullName('');setEmail('');setPhone('');await load()}catch(e){setError(e instanceof Error?e.message:'Could not invite staff.')}finally{setBusy(false)}}
 async function toggle(s:Record<string,unknown>){setBusy(true);try{await api(`/api/merchant/staff/${String(s.id)}/active`,token,{method:'PATCH',body:JSON.stringify({active:!s.active})});await load()}catch(e){setError(e instanceof Error?e.message:'Could not update staff access.')}finally{setBusy(false)}}
 return <section className="surface-card page-table-card"><div className="surface-heading"><div><h2>Staff and access</h2><p>Invite staff with a one-time password setup link. Staff can view products and orders and update fulfilment status.</p></div><Pill>{staff.length} members</Pill></div>{error&&<div className="alert alert-error">{error}</div>}{message&&<div className="alert alert-success">{message}</div>}<form className="inline-create-form" onSubmit={create}><label>Full name<input value={fullName} onChange={e=>setFullName(e.target.value)} required maxLength={120}/></label><label>Email<input type="email" value={email} onChange={e=>setEmail(e.target.value)} required maxLength={180}/></label><label>Mobile number<input value={phone} onChange={e=>setPhone(e.target.value)} required pattern="\+?[0-9]{7,15}"/></label><label>Membership role<select value={role} onChange={e=>setRole(e.target.value)}><option value="STAFF">Staff</option><option value="MANAGER">Manager</option></select></label><Button type="submit" disabled={busy}>Invite staff</Button></form><div className="table-scroll"><table className="data-table"><thead><tr><th>STAFF MEMBER</th><th>PHONE</th><th>ROLE</th><th>EMAIL</th><th>STATUS</th><th></th></tr></thead><tbody>{staff.map(s=><tr key={String(s.id)}><td><b>{String(s.fullName??'')}</b><small>{String(s.email??'')}</small></td><td>{String(s.phone??'')}</td><td>{pretty(String(s.role??''))}</td><td><Pill tone={s.emailVerified?'green':'amber'}>{s.emailVerified?'Verified':'Pending setup'}</Pill></td><td><Pill tone={s.active?'green':'red'}>{s.active?'Active':'Disabled'}</Pill></td><td><Button variant={s.active?'danger':'secondary'} disabled={busy} onClick={()=>void toggle(s)}>{s.active?'Disable':'Enable'}</Button></td></tr>)}</tbody></table></div><p className="quiet-footnote">Deactivating a staff member revokes their active session. Account credentials are never displayed to the store owner.</p></section>
}

function ApplicationsPanel({ token }: { token: string }) {
  const [apps, setApps] = useState<TenantApplication[]>([])
  const [selected, setSelected] = useState<TenantApplication | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [internalNotes, setInternalNotes] = useState('')
  const [applicantNotes, setApplicantNotes] = useState('')
  const [filter, setFilter] = useState('SUBMITTED')
  const [successMessage, setSuccessMessage] = useState('')

  async function load() {
    try {
      const list = await api<TenantApplication[]>(`/api/platform/applications${filter ? `?status=${filter}` : ''}`, token)
      setApps(list)
      if (selected) {
        const updated = list.find(application => application.id === selected.id)
        if (updated) setSelected(updated)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load applications.')
    }
  }

  // eslint-disable-next-line react-hooks/set-state-in-effect, react-hooks/exhaustive-deps
  useEffect(() => { void load() }, [filter])

  async function review(status: string) {
    if (!selected) return
    setBusy(true)
    setError('')
    try {
      const updated = await api<TenantApplication>(`/api/platform/applications/${selected.id}/review`, token, {
        method: 'POST',
        body: JSON.stringify({ status, internalNotes, applicantNotes }),
      })
      setSelected(updated)
      setSuccessMessage(status === 'APPROVED'
        ? 'Application approved and store provisioning completed.'
        : `Application marked ${pretty(status).toLowerCase()}.`)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not update application.')
    } finally {
      setBusy(false)
    }
  }

  async function reviewDoc(doc: AppDocument, status: string) {
    if (!selected) return
    const note = status === 'REJECTED'
      ? window.prompt('Reason for rejecting this document:')
      : 'Verified against the submitted information'
    if (status === 'REJECTED' && !note) return
    setBusy(true)
    setError('')
    try {
      await api(`/api/platform/applications/${selected.id}/documents/${doc.id}/review`, token, {
        method: 'PATCH',
        body: JSON.stringify({ status, note }),
      })
      await load()
      const updated = await api<TenantApplication>(`/api/platform/applications/${selected.id}`, token)
      setSelected(updated)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not review document.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="surface-card page-table-card">
      <div className="surface-heading">
        <div>
          <h2>Store applications</h2>
          <p>Review submissions here. Choose Draft to find saved store contacts and follow up with applicants who have not finished.</p>
        </div>
        <div className="form-inline">
          <select value={filter} onChange={event => setFilter(event.target.value)} aria-label="Filter applications by status">
            <option value="DRAFT">Draft</option>
            <option value="SUBMITTED">Submitted</option>
            <option value="UNDER_REVIEW">Under review</option>
            <option value="NEEDS_CORRECTION">Needs correction</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
            <option value="">All statuses</option>
          </select>
          <Button variant="secondary" onClick={() => void load()}>Refresh</Button>
        </div>
      </div>
      {error && <div className="alert alert-error">{error}</div>}
      {successMessage && <div className="alert alert-success">{successMessage}</div>}
      <div className="table-scroll">
        <table className="data-table">
          <thead><tr><th>STORE</th><th>OWNER / CONTACT</th><th>PLAN</th><th>STATUS</th><th>SUBMITTED / SAVED</th><th /></tr></thead>
          <tbody>
            {apps.length === 0
              ? <tr><td colSpan={6} className="table-message">No applications found for this filter. Use Draft to inspect incomplete applications.</td></tr>
              : apps.map(application => (
                <tr key={application.id}>
                  <td><b>{application.storeName}</b><small>{application.storeCode} · /store/{application.slug}</small></td>
                  <td>{application.ownerName}<small>{application.ownerEmail || 'Owner details pending'} · {application.phone}</small></td>
                  <td>{application.selectedPlanCode}<small>{money(application.selectedPlanPriceNpr)}/month at application time</small></td>
                  <td><Pill tone={application.status === 'APPROVED' ? 'green' : application.status === 'REJECTED' ? 'red' : 'amber'}>{pretty(application.status)}</Pill></td>
                  <td>{dateLabel(application.submittedAt ?? application.createdAt)}</td>
                  <td><Button variant="secondary" onClick={() => {
                    setSelected(application)
                    setInternalNotes(application.reviewerNotes ?? '')
                    setApplicantNotes(application.publicReviewNotes ?? '')
                    setSuccessMessage('')
                  }}>{application.status === 'DRAFT' && !application.ownerEmail ? 'Open lead' : 'Review'}</Button></td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {selected && (
        <div className="application-review">
          <header className="application-review-header">
            <div>
              <span className="application-review-kicker">Application review</span>
              <div className="application-review-title-line">
                <h3>{selected.storeName}</h3>
                <Pill tone={selected.status === 'APPROVED' ? 'green' : selected.status === 'REJECTED' ? 'red' : 'amber'}>{pretty(selected.status)}</Pill>
              </div>
              <p>{selected.storeCode} <span>·</span> /store/{selected.slug}</p>
            </div>
            <Button variant="ghost" className="application-review-close" onClick={() => setSelected(null)} aria-label="Close application review">Close <span aria-hidden="true">×</span></Button>
          </header>

          <section className="application-review-owner" aria-label="Applicant contact details">
            <div className="application-review-avatar" aria-hidden="true">{selected.ownerName.trim().charAt(0).toUpperCase() || '?'}</div>
            <div className="application-review-owner-copy">
              <strong>{selected.ownerName}</strong>
              <span>{selected.ownerEmail || 'Owner account not created yet'}</span>
              <span>{selected.phone || 'No phone provided'}</span>
            </div>
            <div className="application-review-verification">
              <span>Email verification</span>
              <Pill tone={selected.emailVerified ? 'green' : 'amber'}>{selected.emailVerified ? 'Verified' : 'Not verified'}</Pill>
            </div>
          </section>
          {selected.status === 'DRAFT' && !selected.ownerEmail && <div className="application-review-lead-note"><strong>Saved store lead</strong><span>The owner has not finished account setup yet. Use the phone number above to follow up.</span></div>}

          <section className="application-review-section">
            <div className="application-review-section-heading">
              <div><span>01</span><div><h4>Business and identity</h4><p>Applicant and registration information</p></div></div>
            </div>
            <div className="application-review-facts">
              <div><span>Citizenship number</span><strong>{selected.citizenshipNumber || 'Not supplied'}</strong></div>
              <div><span>Business type</span><strong>{selected.businessType ? pretty(selected.businessType) : 'Not supplied'}</strong></div>
              <div><span>PAN / VAT number</span><strong>{selected.panVatNumber || 'Not supplied'}</strong></div>
              <div><span>Registration authority</span><strong>{selected.registrationAuthority || 'Not supplied'}</strong></div>
              <div><span>Registration number</span><strong>{selected.registrationNumber || 'Not supplied'}</strong></div>
              <div className="application-review-fact-wide"><span>Registered address</span><strong>{[selected.registeredAddress, selected.municipality, selected.district, selected.province].filter(Boolean).join(', ') || 'Not supplied'}</strong></div>
            </div>
          </section>

          <section className="application-review-section">
            <div className="application-review-section-heading">
              <div><span>02</span><div><h4>Supporting documents</h4><p>Review and verify each uploaded file</p></div></div>
              <Pill>{selected.documents.length} {selected.documents.length === 1 ? 'file' : 'files'}</Pill>
            </div>
            {selected.documents.length === 0
              ? <div className="application-review-empty"><span aria-hidden="true">▤</span><div><strong>No documents uploaded</strong><p>This application does not have any supporting files yet.</p></div></div>
              : <div className="application-review-documents">
                {selected.documents.map(doc => (
                  <article className="application-review-document" key={doc.id}>
                    <div className="application-review-document-icon" aria-hidden="true">▤</div>
                    <div className="application-review-document-info">
                      <strong>{pretty(doc.documentType)}</strong>
                      <span>{doc.originalFilename}</span>
                      <small>{(doc.sizeBytes / 1024).toFixed(0)} KB <span>·</span> Uploaded {dateLabel(doc.uploadedAt)}</small>
                      {doc.reviewerNote && <small className="application-review-document-note">Reviewer note: {doc.reviewerNote}</small>}
                    </div>
                    <div className="application-review-document-status"><Pill tone={doc.verificationStatus === 'VERIFIED' ? 'green' : doc.verificationStatus === 'REJECTED' ? 'red' : 'amber'}>{pretty(doc.verificationStatus)}</Pill></div>
                    <div className="application-review-document-actions">
                      <Button variant="secondary" onClick={() => window.open(`${API_BASE_URL}/api/platform/applications/${selected.id}/documents/${doc.id}`, '_blank', 'noopener,noreferrer')}>Open file</Button>
                      {doc.verificationStatus !== 'VERIFIED' && doc.verificationStatus !== 'SUPERSEDED' && <Button disabled={busy} onClick={() => void reviewDoc(doc, 'VERIFIED')}>Verify</Button>}
                      {doc.verificationStatus !== 'REJECTED' && doc.verificationStatus !== 'SUPERSEDED' && <Button variant="danger" disabled={busy} onClick={() => void reviewDoc(doc, 'REJECTED')}>Reject</Button>}
                    </div>
                  </article>
                ))}
              </div>}
          </section>

          <section className="application-review-section application-review-notes">
            <div className="application-review-section-heading">
              <div><span>03</span><div><h4>Review notes</h4><p>Keep internal context and applicant updates separate</p></div></div>
            </div>
            <div className="application-review-note-fields">
              <label><span>Internal notes <small>Only visible to platform staff</small></span><textarea rows={3} value={internalNotes} onChange={event => setInternalNotes(event.target.value)} maxLength={2000} placeholder="Add context for the review team…" /></label>
              <label><span>Message to applicant <small>Visible to the applicant</small></span><textarea rows={3} value={applicantNotes} onChange={event => setApplicantNotes(event.target.value)} maxLength={2000} placeholder="Explain any requested changes or next steps…" /></label>
            </div>
          </section>

          {selected.ownerEmail && <footer className="application-review-footer">
            <div><strong>Application decision</strong><span>Choose the next step for this application.</span></div>
            <div className="product-form-actions">
              <Button variant="secondary" disabled={busy} onClick={() => void review('UNDER_REVIEW')}>Mark under review</Button>
              <Button variant="secondary" disabled={busy} onClick={() => void review('NEEDS_CORRECTION')}>Request corrections</Button>
              <Button variant="danger" disabled={busy} onClick={() => void review('REJECTED')}>Reject application</Button>
              <Button disabled={busy} onClick={() => void review('APPROVED')}>Approve and activate trial</Button>
            </div>
          </footer>}
        </div>
      )}
    </section>
  )
}

function PlatformUsersPanel({ token, user }: { token: string; user: AuthUser }) {
 const [users,setUsers]=useState<PlatformUserRecord[]>([]);const [email,setEmail]=useState('');const [fullName,setFullName]=useState('');const [password,setPassword]=useState('');const [error,setError]=useState('');const [message,setMessage]=useState('');const [busy,setBusy]=useState(false)
 async function load(){try{setUsers(await api<PlatformUserRecord[]>('/api/platform/users',token))}catch(e){setError(e instanceof Error?e.message:'Could not load platform users.')}}
 // eslint-disable-next-line react-hooks/set-state-in-effect, react-hooks/exhaustive-deps
 useEffect(()=>{void load()},[])
 async function create(e:FormEvent){e.preventDefault();setBusy(true);setError('');setMessage('');try{await api('/api/platform/users',token,{method:'POST',body:JSON.stringify({email,fullName,temporaryPassword:password,role:'PLATFORM_ADMIN'})});setEmail('');setFullName('');setPassword('');setMessage('Platform account created. Share the temporary password securely.');await load()}catch(e){setError(e instanceof Error?e.message:'Could not create platform account.')}finally{setBusy(false)}}
 async function toggle(u:PlatformUserRecord){setBusy(true);try{await api(`/api/platform/users/${u.id}/enabled`,token,{method:'PATCH',body:JSON.stringify({enabled:!u.enabled})});await load()}catch(e){setError(e instanceof Error?e.message:'Could not update user.')}finally{setBusy(false)}}
 async function revoke(u:PlatformUserRecord){setBusy(true);setError('');try{await api(`/api/platform/users/${u.id}/revoke-session`,token,{method:'POST'});setMessage(`All active sessions revoked for ${u.username}.`)}catch(e){setError(e instanceof Error?e.message:'Could not revoke sessions.')}finally{setBusy(false)}}
 async function resetMfa(u:PlatformUserRecord){if(!window.confirm(`Reset the authenticator for ${u.username}? They will lose unused recovery codes and use email OTP until they enroll a new authenticator.`))return;setBusy(true);setError('');setMessage('');try{const result=await api<{message:string}>(`/api/platform/users/${u.id}/mfa/reset`,token,{method:'POST'});setMessage(result.message);await load()}catch(e){setError(e instanceof Error?e.message:'Could not reset MFA.')}finally{setBusy(false)}}
 return <section className="surface-card page-table-card"><div className="surface-heading"><div><h2>Platform users</h2><p>Separate platform staff accounts from tenant owners and staff.</p></div><Pill>{users.length} accounts</Pill></div>{error&&<div className="alert alert-error">{error}</div>}{message&&<div className="alert alert-success">{message}</div>}<form className="inline-create-form" onSubmit={create}><label>Full name<input value={fullName} onChange={e=>setFullName(e.target.value)} required maxLength={120}/></label><label>Email<input type="email" value={email} onChange={e=>setEmail(e.target.value)} required maxLength={180}/></label><label>Temporary password<input type="password" value={password} onChange={e=>setPassword(e.target.value)} required minLength={16} maxLength={72}/></label><Button type="submit" disabled={busy}>Create platform user</Button></form><div className="table-scroll"><table className="data-table"><thead><tr><th>USER</th><th>ROLE</th><th>CREATED</th><th>ACCESS</th><th>MFA</th><th>ACTION</th></tr></thead><tbody>{users.map(u=><tr key={u.id}><td><b>{u.fullName}</b><small>{u.username}</small></td><td>{pretty(u.role)}</td><td>{dateLabel(u.createdAt)}</td><td><Pill tone={u.enabled?'green':'red'}>{u.enabled?'Enabled':'Disabled'}</Pill></td><td><Pill tone={u.mfaEnabled?'green':'blue'}>{u.mfaEnabled?'Authenticator':'Email OTP'}</Pill></td><td><Button variant="secondary" disabled={busy||!u.enabled} onClick={()=>void revoke(u)}>Revoke all sessions</Button> <Button variant="secondary" disabled={busy||!u.enabled||!u.mfaEnabled||u.username.toLowerCase()===user.username.toLowerCase()} onClick={()=>void resetMfa(u)}>Reset MFA</Button> <Button variant={u.enabled?'danger':'secondary'} disabled={busy||u.role==='SUPERADMIN'} onClick={()=>void toggle(u)}>{u.enabled?'Disable':'Enable'}</Button></td></tr>)}</tbody></table></div><p className="quiet-footnote">Disabling an account or resetting its MFA revokes its active sessions. A reset can be performed only by another superadmin.</p></section>
}

function PlansPanel({ token }: { token: string }) {
 const [plans,setPlans]=useState<PlanOption[]>([]);const [error,setError]=useState('');const [message,setMessage]=useState('');const [busy,setBusy]=useState(false);const [draft,setDraft]=useState({code:'',name:'',description:'',monthlyPriceNpr:'',annualPriceNpr:'',trialDays:'14',maxProducts:'100',maxStaff:'2',published:true})
 async function load(){try{setPlans(await api<PlanOption[]>('/api/platform/plans',token))}catch(e){setError(e instanceof Error?e.message:'Could not load plans.')}}
 // eslint-disable-next-line react-hooks/set-state-in-effect, react-hooks/exhaustive-deps
 useEffect(()=>{void load()},[])
 async function create(e:FormEvent){e.preventDefault();setBusy(true);setError('');try{await api('/api/platform/plans',token,{method:'POST',body:JSON.stringify({...draft,code:draft.code.toUpperCase(),monthlyPriceNpr:Number(draft.monthlyPriceNpr),annualPriceNpr:Number(draft.annualPriceNpr),trialDays:Number(draft.trialDays),maxProducts:Number(draft.maxProducts),maxStaff:Number(draft.maxStaff)})});setMessage('Plan created.');setDraft({code:'',name:'',description:'',monthlyPriceNpr:'',annualPriceNpr:'',trialDays:'14',maxProducts:'100',maxStaff:'2',published:true});await load()}catch(e){setError(e instanceof Error?e.message:'Could not create plan.')}finally{setBusy(false)}}
 async function save(p:PlanOption){setBusy(true);setError('');try{await api(`/api/platform/plans/${p.id}`,token,{method:'PATCH',body:JSON.stringify({...p,monthlyPriceNpr:Number(p.monthlyPriceNpr),annualPriceNpr:Number(p.annualPriceNpr),published:p.published})});setMessage(`${p.name} updated.`);await load()}catch(e){setError(e instanceof Error?e.message:'Could not update plan.')}finally{setBusy(false)}}
 async function archive(p:PlanOption){if(!window.confirm(`Archive ${p.name}?`))return;setBusy(true);try{await api(`/api/platform/plans/${p.id}`,token,{method:'DELETE'});await load()}catch(e){setError(e instanceof Error?e.message:'Could not archive plan.')}finally{setBusy(false)}}
 return <section className="surface-card page-table-card"><div className="surface-heading"><div><h2>Plans and pricing</h2><p>Prices are in NPR. Existing subscriptions retain their agreed price snapshot.</p></div></div>{error&&<div className="alert alert-error">{error}</div>}{message&&<div className="alert alert-success">{message}</div>}<form className="inline-create-form" onSubmit={create}><label>Plan code<input value={draft.code} onChange={e=>setDraft({...draft,code:e.target.value.toUpperCase()})} required pattern="[A-Z][A-Z0-9_]{2,39}" title="Use 3–40 uppercase letters, numbers or underscores."/></label><label>Name<input value={draft.name} onChange={e=>setDraft({...draft,name:e.target.value})} required/></label><label>Monthly price (NPR)<input type="number" min="0" step="0.01" value={draft.monthlyPriceNpr} onChange={e=>setDraft({...draft,monthlyPriceNpr:e.target.value})} required/></label><label>Annual price (NPR)<input type="number" min="0" step="0.01" value={draft.annualPriceNpr} onChange={e=>setDraft({...draft,annualPriceNpr:e.target.value})} required/></label><label>Trial days<input type="number" min="0" max="365" value={draft.trialDays} onChange={e=>setDraft({...draft,trialDays:e.target.value})} required/></label><label>Max products<input type="number" min="0" value={draft.maxProducts} onChange={e=>setDraft({...draft,maxProducts:e.target.value})} required/></label><label>Max staff<input type="number" min="1" value={draft.maxStaff} onChange={e=>setDraft({...draft,maxStaff:e.target.value})} required/></label><Button type="submit" disabled={busy}>Add plan</Button></form><div className="table-scroll"><table className="data-table"><thead><tr><th>PLAN</th><th>MONTHLY NPR</th><th>ANNUAL NPR</th><th>TRIAL DAYS</th><th>PRODUCTS</th><th>STAFF</th><th>STATUS</th><th></th></tr></thead><tbody>{plans.map(p=><tr key={p.id}><td><b>{p.name}</b><small>{p.code}</small></td><td><input aria-label={`${p.name} monthly price`} type="number" min="0" step="0.01" value={p.monthlyPriceNpr} onChange={e=>setPlans(old=>old.map(x=>x.id===p.id?{...x,monthlyPriceNpr:Number(e.target.value)}:x))}/></td><td><input aria-label={`${p.name} annual price`} type="number" min="0" step="0.01" value={p.annualPriceNpr} onChange={e=>setPlans(old=>old.map(x=>x.id===p.id?{...x,annualPriceNpr:Number(e.target.value)}:x))}/></td><td>{p.trialDays}</td><td>{p.maxProducts}</td><td>{p.maxStaff}</td><td><Pill tone={p.published?'green':'red'}>{p.published?'Published':'Archived'}</Pill></td><td><Button disabled={busy} onClick={()=>void save(p)}>Save</Button> <Button variant="danger" disabled={busy} onClick={()=>void archive(p)}>Archive</Button></td></tr>)}</tbody></table></div></section>
}

function SubscriptionsPanel({ token }: { token: string }) {
 const [rows,setRows]=useState<Array<Record<string,unknown>>>([]);const [plans,setPlans]=useState<PlanOption[]>([]);const [error,setError]=useState('');const [message,setMessage]=useState('');const [busy,setBusy]=useState(false);const [drafts,setDrafts]=useState<Record<string,{status:string;planCode:string;trialEndsAt:string}>>({})
 async function load(){try{const [subs,available]=await Promise.all([api<Array<Record<string,unknown>>>('/api/platform/subscriptions',token),api<PlanOption[]>('/api/platform/plans',token)]);setRows(subs);setPlans(available);setDrafts(Object.fromEntries(subs.map(s=>[String(s.id),{status:String(s.status),planCode:String(s.planCode),trialEndsAt:s.trialEndsAt?new Date(String(s.trialEndsAt)).toISOString().slice(0,16):''}]))) }catch(e){setError(e instanceof Error?e.message:'Could not load subscriptions.')}}
 // eslint-disable-next-line react-hooks/set-state-in-effect, react-hooks/exhaustive-deps
 useEffect(()=>{void load()},[])
 async function save(id:string){const d=drafts[id];if(!d)return;setBusy(true);setError('');setMessage('');try{await api(`/api/platform/subscriptions/${id}`,token,{method:'PATCH',body:JSON.stringify({status:d.status==='ACTIVE'?null:d.status,planCode:d.planCode,trialEndsAt:d.status==='TRIAL'&&d.trialEndsAt?new Date(d.trialEndsAt).toISOString():null})});setMessage('Subscription updated.');await load()}catch(e){setError(e instanceof Error?e.message:'Could not update subscription.')}finally{setBusy(false)}}
 return <section className="surface-card page-table-card"><div className="surface-heading"><div><h2>Subscriptions and trial status</h2><p>Assign a plan, extend a trial, or change access status. Paid activation happens when a verified invoice payment is recorded in Billing.</p></div><Button variant="secondary" onClick={()=>void load()}>Refresh</Button></div>{error&&<div className="alert alert-error">{error}</div>}{message&&<div className="alert alert-success">{message}</div>}<div className="table-scroll"><table className="data-table"><thead><tr><th>TENANT</th><th>PLAN</th><th>PRICE / MONTH</th><th>STATUS</th><th>TRIAL ENDS</th><th></th></tr></thead><tbody>{rows.map(r=>{const id=String(r.id);const d=drafts[id]??{status:String(r.status),planCode:String(r.planCode),trialEndsAt:''};return <tr key={id}><td><b>{String(r.businessName??'')}</b><small>{String(r.storeCode??'')}</small></td><td><select value={d.planCode} onChange={e=>setDrafts(old=>({...old,[id]:{...d,planCode:e.target.value}}))}>{plans.map(p=><option key={p.code} value={p.code}>{p.name}</option>)}</select></td><td>{moneyExact(Number(r.monthlyPriceNpr))}{Number(r.discountAmountNpr)>0&&<small>{moneyExact(Number(r.discountAmountNpr))} first invoice</small>}</td><td><select value={d.status} onChange={e=>setDrafts(old=>({...old,[id]:{...d,status:e.target.value}}))}><option value="TRIAL">Trial</option><option value="ACTIVE" disabled>Active · paid invoice</option><option value="PAST_DUE">Past due</option><option value="SUSPENDED">Suspended</option><option value="CANCELLED">Cancelled</option></select></td><td><input aria-label={`Trial end for ${String(r.businessName??'tenant')}`} type="datetime-local" value={d.trialEndsAt} disabled={d.status!=='TRIAL'} onChange={e=>setDrafts(old=>({...old,[id]:{...d,trialEndsAt:e.target.value}}))}/></td><td><Button disabled={busy} onClick={()=>void save(id)}>Save</Button></td></tr>})}</tbody></table></div></section>
}

function BillingPanel({ token }: { token: string }) {
 const [subscriptions,setSubscriptions]=useState<Array<Record<string,unknown>>>([]);const [invoices,setInvoices]=useState<Array<Record<string,unknown>>>([]);const [selected,setSelected]=useState('');const [cycle,setCycle]=useState('MONTHLY');const [dueOn,setDueOn]=useState(()=>dateInputAfter(7));const [paymentId,setPaymentId]=useState('');const [paymentMethod,setPaymentMethod]=useState('BANK_TRANSFER');const [paymentReference,setPaymentReference]=useState('');const [paymentNotes,setPaymentNotes]=useState('');const [voidId,setVoidId]=useState('');const [voidReason,setVoidReason]=useState('');const [error,setError]=useState('');const [message,setMessage]=useState('');const [busy,setBusy]=useState(false)
 async function load(){try{const [subs,items]=await Promise.all([api<Array<Record<string,unknown>>>('/api/platform/subscriptions',token),api<Array<Record<string,unknown>>>('/api/platform/subscription-invoices',token)]);setSubscriptions(subs);setInvoices(items);setSelected(old=>old||String(subs[0]?.id??''))}catch(e){setError(e instanceof Error?e.message:'Could not load subscription billing.')}}
 // eslint-disable-next-line react-hooks/set-state-in-effect, react-hooks/exhaustive-deps
 useEffect(()=>{void load()},[])
 async function issue(e:FormEvent){e.preventDefault();const sub=subscriptions.find(s=>String(s.id)===selected);if(!sub)return;if(!window.confirm(`Issue a ${cycle.toLowerCase()} invoice for ${String(sub.businessName)}? The plan price and service period will be saved on the invoice.`))return;setBusy(true);setError('');setMessage('');try{const result=await api<Record<string,unknown>>(`/api/platform/subscriptions/${selected}/invoices`,token,{method:'POST',body:JSON.stringify({billingCycle:cycle,dueOn})});setMessage(`Invoice ${String(result.invoiceNumber)} issued.`);await load()}catch(e){setError(e instanceof Error?e.message:'Could not issue invoice.')}finally{setBusy(false)}}
 async function recordPayment(e:FormEvent){e.preventDefault();const item=invoices.find(i=>String(i.id)===paymentId);if(!item)return;if(!window.confirm(`Confirm you verified full payment of ${moneyExact(String(item.amountDueNpr))} for invoice ${String(item.invoiceNumber)}? This records an immutable payment record.`))return;setBusy(true);setError('');setMessage('');try{const result=await api<Record<string,unknown>>(`/api/platform/subscription-invoices/${paymentId}/payment`,token,{method:'POST',body:JSON.stringify({paymentMethod,paymentReference,paymentNotes,verified:true})});setMessage(result.subscriptionStatus==='CANCELLED'?`Payment recorded for ${String(item.invoiceNumber)}. The subscription remains cancelled.`:`Payment recorded for ${String(item.invoiceNumber)}.`);setPaymentId('');setPaymentReference('');setPaymentNotes('');await load()}catch(e){setError(e instanceof Error?e.message:'Could not record payment.')}finally{setBusy(false)}}
 async function voidInvoice(e:FormEvent){e.preventDefault();setBusy(true);setError('');setMessage('');try{const item=invoices.find(i=>String(i.id)===voidId);await api(`/api/platform/subscription-invoices/${voidId}/void`,token,{method:'POST',body:JSON.stringify({reason:voidReason})});setMessage(`${String(item?.invoiceNumber??'Invoice')} voided.`);setVoidId('');setVoidReason('');await load()}catch(e){setError(e instanceof Error?e.message:'Could not void invoice.')}finally{setBusy(false)}}
 return <section className="surface-card page-table-card"><div className="surface-heading"><div><h2>Manual subscription billing</h2><p>Issue invoices and record full payments after verifying the funds were received by Byawasaya.</p></div><Button variant="secondary" onClick={()=>void load()}>Refresh</Button></div>{error&&<div className="alert alert-error">{error}</div>}{message&&<div className="alert alert-success">{message}</div>}<div className="notice-note"><span>i</span><p>Platform subscription invoices use NPR plan-price snapshots. No platform payment gateway or automatic collection is enabled. eSewa and Khalti store checkouts still settle directly to each tenant’s merchant account.</p></div><form className="inline-create-form" onSubmit={issue}><label>Tenant subscription<select value={selected} onChange={e=>setSelected(e.target.value)} required><option value="" disabled>Select a subscription</option>{subscriptions.map(s=><option key={String(s.id)} value={String(s.id)}>{String(s.businessName)} · {String(s.storeCode)} · {String(s.planName)} ({pretty(String(s.status))})</option>)}</select></label><label>Billing cycle<select value={cycle} onChange={e=>setCycle(e.target.value)}><option value="MONTHLY">Monthly · {moneyExact(String(subscriptions.find(s=>String(s.id)===selected)?.monthlyPriceNpr??0))}</option><option value="ANNUAL">Annual · {moneyExact(String(subscriptions.find(s=>String(s.id)===selected)?.annualPriceNpr??0))}</option></select></label><label>Payment due<input type="date" value={dueOn} onChange={e=>setDueOn(e.target.value)} required/></label><Button type="submit" disabled={busy||!selected}>Issue invoice</Button></form><div className="table-scroll"><table className="data-table"><thead><tr><th>INVOICE / TENANT</th><th>SERVICE PERIOD</th><th>AMOUNT</th><th>DUE</th><th>STATUS</th><th>PAYMENT / ACTION</th></tr></thead><tbody>{invoices.map(i=>{const id=String(i.id);const status=String(i.status);return <tr key={id}><td><b>{String(i.invoiceNumber)}</b><small>{String(i.businessName)} · {String(i.storeCode)}</small><small>{String(i.planName)} · {pretty(String(i.billingCycle))}</small></td><td>{dateOnly(String(i.servicePeriodStart))}<small>through {dateOnly(String(i.servicePeriodEnd))}</small></td><td><b>{moneyExact(String(i.amountDueNpr))}</b>{Number(i.discountNpr)>0&&<small>Includes {moneyExact(String(i.discountNpr))} first-invoice discount</small>}</td><td>{dateOnly(String(i.dueOn))}</td><td><Pill tone={status==='PAID'?'green':status==='OVERDUE'||status==='VOID'?'red':'amber'}>{pretty(status)}</Pill></td><td>{status==='PAID'?<><small>{pretty(String(i.paymentMethod??''))} · {String(i.paymentReference??'')}</small><small>Recorded {dateLabel(String(i.receivedAt??''))} by {String(i.recordedBy??'')}</small>{i.subscriptionStatus==='CANCELLED'&&<small>Subscription remains cancelled</small>}</>:status==='OPEN'||status==='OVERDUE'?<div className="billing-row-actions"><Button disabled={busy} onClick={()=>{setPaymentId(id);setVoidId('')}}>Record payment</Button><Button variant="secondary" disabled={busy} onClick={()=>{setVoidId(id);setPaymentId('')}}>Void</Button></div>:<small>{String(i.voidReason??'')}</small>}{paymentId===id&&<form className="billing-inline-form" onSubmit={recordPayment}><b>Confirm full receipt of {moneyExact(String(i.amountDueNpr))}</b><label>Received by<select value={paymentMethod} onChange={e=>setPaymentMethod(e.target.value)}><option value="BANK_TRANSFER">Bank transfer</option><option value="ESEWA">eSewa · manual verification</option><option value="KHALTI">Khalti · manual verification</option><option value="CASH">Cash</option><option value="OTHER">Other</option></select></label><label>Transaction / receipt reference<input value={paymentReference} onChange={e=>setPaymentReference(e.target.value)} required maxLength={120}/></label><label>Notes (optional)<input value={paymentNotes} onChange={e=>setPaymentNotes(e.target.value)} maxLength={500}/></label><Button type="submit" disabled={busy}>Verified and received</Button><Button type="button" variant="secondary" onClick={()=>setPaymentId('')}>Cancel</Button></form>}{voidId===id&&<form className="billing-inline-form" onSubmit={voidInvoice}><b>Void {String(i.invoiceNumber)}</b><label>Reason<textarea value={voidReason} onChange={e=>setVoidReason(e.target.value)} required maxLength={500}/></label><Button type="submit" variant="danger" disabled={busy}>Void invoice</Button><Button type="button" variant="secondary" onClick={()=>setVoidId('')}>Cancel</Button></form>}</td></tr>})}</tbody></table></div>{!invoices.length&&<EmptyState title="No subscription invoices" text="Issue the first invoice from a tenant subscription above."/>}<p className="quiet-footnote">Issuing a monthly or annual invoice uses the subscription’s saved plan price. Any approved promotion is applied to the first non-void invoice only. Payment records are full amount, append-only, and administrator-entered.</p></section>
}

function TenantBillingPanel({ token }: { token: string }) {
 const [invoices,setInvoices]=useState<Array<Record<string,unknown>>>([]);const [error,setError]=useState('')
 useEffect(()=>{api<Array<Record<string,unknown>>>('/api/tenant-billing/invoices',token).then(setInvoices).catch(e=>setError(e instanceof Error?e.message:'Could not load your invoices.'))},[token])
 return <section className="surface-card page-table-card"><div className="surface-heading"><div><h2>Your subscription invoices</h2><p>Byawasaya plan charges are invoiced manually. Store checkout funds continue to settle to your own merchant account.</p></div><Pill>{invoices.length} invoices</Pill></div>{error&&<div className="alert alert-error">{error}</div>}{invoices.length?invoices.map(i=>{const status=String(i.status);return <article className="invoice-card" key={String(i.id)}><div className="surface-heading"><div><h3>{String(i.invoiceNumber)}</h3><p>{String(i.planName)} · {pretty(String(i.billingCycle))} subscription</p></div><Pill tone={status==='PAID'?'green':status==='OVERDUE'||status==='VOID'?'red':'amber'}>{pretty(status)}</Pill></div><div className="invoice-details"><div><small>SERVICE PERIOD</small><b>{dateOnly(String(i.servicePeriodStart))} – {dateOnly(String(i.servicePeriodEnd))}</b></div><div><small>PAYMENT DUE</small><b>{dateOnly(String(i.dueOn))}</b></div><div><small>AMOUNT DUE · NPR</small><b>{moneyExact(String(i.amountDueNpr))}</b></div></div><div className="invoice-total-lines"><span>Plan price <b>{moneyExact(String(i.subtotalNpr))}</b></span>{Number(i.discountNpr)>0&&<span>Promotion discount <b>− {moneyExact(String(i.discountNpr))}</b></span>}<span className="invoice-total">Total due <b>{moneyExact(String(i.amountDueNpr))}</b></span></div>{status==='PAID'&&<p className="quiet-footnote">Payment recorded {dateLabel(String(i.receivedAt??''))} · {pretty(String(i.paymentMethod??''))} · Reference {String(i.paymentReference??'')}</p>}{status==='PAID'&&i.subscriptionStatus==='CANCELLED'&&<p className="quiet-footnote">The invoice is paid, but your subscription remains cancelled. Contact Byawasaya if you need service reactivated.</p>}{status==='VOID'&&<p className="quiet-footnote">This invoice was voided. {String(i.voidReason??'')}</p>}{status==='OPEN'||status==='OVERDUE'?<div className="notice-note"><span>i</span><p>Contact Byawasaya using your account support channel for payment instructions. Your payment will appear here after it is verified and recorded.</p></div>:null}</article>}):!error?<EmptyState title="No invoices yet" text="Your Byawasaya subscription invoices will appear here."/>:null}<p className="quiet-footnote">These are Byawasaya subscription statements. Tax treatment and any legally required tax invoice are handled separately by the platform.</p></section>
}

function TenantBillingDashboard({ token, user, onLogout }: { token: string; user: AuthUser; onLogout: () => void }) {
 return <DashboardFrame user={user} title="Plan & billing" eyebrow="YOUR BUSINESS ACCOUNT" active="billing" onTab={tab=>{if(tab!=='billing')navigate('/tenant/dashboard')}} onLogout={onLogout}><TenantBillingPanel token={token}/></DashboardFrame>
}

function PromotionsPanel({ token }: { token: string }) {
 const [promos,setPromos]=useState<Promo[]>([]);const [error,setError]=useState('');const [message,setMessage]=useState('');const [busy,setBusy]=useState(false);const [code,setCode]=useState('');const [description,setDescription]=useState('');const [discountType,setDiscountType]=useState('PERCENT');const [discountValue,setDiscountValue]=useState('10');const [startsAt,setStartsAt]=useState(new Date().toISOString().slice(0,16));const [endsAt,setEndsAt]=useState('');const [maxRedemptions,setMaxRedemptions]=useState('100');const [maxPerTenant,setMaxPerTenant]=useState('1');const [eligible,setEligible]=useState('STARTER,PLUS,PRO')
 async function load(){try{setPromos(await api<Promo[]>('/api/platform/promotions',token))}catch(e){setError(e instanceof Error?e.message:'Could not load promotions.')}}
 // eslint-disable-next-line react-hooks/set-state-in-effect, react-hooks/exhaustive-deps
 useEffect(()=>{void load()},[])
 async function create(e:FormEvent){e.preventDefault();setBusy(true);setError('');setMessage('');try{await api('/api/platform/promotions',token,{method:'POST',body:JSON.stringify({code,description,discountType,discountValue:Number(discountValue),startsAt:new Date(startsAt).toISOString(),endsAt:endsAt?new Date(endsAt).toISOString():null,maxRedemptions:maxRedemptions?Number(maxRedemptions):null,maxPerTenant:Number(maxPerTenant),eligiblePlanCodes:eligible.split(',').map(x=>x.trim().toUpperCase()).filter(Boolean),active:true})});setMessage('Promotion created.');setCode('');await load()}catch(e){setError(e instanceof Error?e.message:'Could not create promotion.')}finally{setBusy(false)}}
 async function toggle(p:Promo){setBusy(true);try{await api(`/api/platform/promotions/${p.id}/active`,token,{method:'PATCH',body:JSON.stringify({active:!p.active})});await load()}catch(e){setError(e instanceof Error?e.message:'Could not update promotion.')}finally{setBusy(false)}}
 return <section className="surface-card page-table-card"><div className="surface-heading"><div><h2>Promotions and discounts</h2><p>Control validity, eligible plans and redemption limits.</p></div></div>{error&&<div className="alert alert-error">{error}</div>}{message&&<div className="alert alert-success">{message}</div>}<form className="inline-create-form" onSubmit={create}><label>Promo code<input value={code} onChange={e=>setCode(e.target.value.toUpperCase())} required pattern="[A-Z0-9_-]{3,60}"/></label><label>Description<input value={description} onChange={e=>setDescription(e.target.value)} maxLength={500}/></label><label>Discount type<select value={discountType} onChange={e=>setDiscountType(e.target.value)}><option value="PERCENT">Percentage</option><option value="FIXED">Fixed NPR amount</option></select></label><label>Discount value<input type="number" min="0.01" step="0.01" max={discountType==='PERCENT'?100:undefined} value={discountValue} onChange={e=>setDiscountValue(e.target.value)} required/></label><label>Starts at<input type="datetime-local" value={startsAt} onChange={e=>setStartsAt(e.target.value)} required/></label><label>Ends at (optional)<input type="datetime-local" value={endsAt} onChange={e=>setEndsAt(e.target.value)}/></label><label>Max redemptions<input type="number" min="1" value={maxRedemptions} onChange={e=>setMaxRedemptions(e.target.value)}/></label><label>Per tenant limit<input type="number" min="1" value={maxPerTenant} onChange={e=>setMaxPerTenant(e.target.value)} required/></label><label>Eligible plan codes<input value={eligible} onChange={e=>setEligible(e.target.value)} required/><small>Comma-separated codes, e.g. STARTER,PLUS,PRO</small></label><Button type="submit" disabled={busy}>Create promotion</Button></form><div className="table-scroll"><table className="data-table"><thead><tr><th>CODE</th><th>DISCOUNT</th><th>VALIDITY</th><th>REDEMPTIONS</th><th>STATUS</th><th></th></tr></thead><tbody>{promos.map(p=><tr key={p.id}><td><b>{p.code}</b><small>{p.description}</small></td><td>{p.discountType==='PERCENT'?`${p.discountValue}%`:`${money(p.discountValue)} off`}</td><td>{dateLabel(p.startsAt)}<small>{p.endsAt?`Until ${dateLabel(p.endsAt)}`:'No expiry'}</small></td><td>{p.redeemedCount}{p.maxRedemptions?` / ${p.maxRedemptions}`:''}</td><td><Pill tone={p.active?'green':'red'}>{p.active?'Active':'Inactive'}</Pill></td><td><Button variant="secondary" disabled={busy} onClick={()=>void toggle(p)}>{p.active?'Deactivate':'Activate'}</Button></td></tr>)}</tbody></table></div></section>
}

function AuditPanel({ token }: { token: string }) {
 const [rows,setRows]=useState<Array<Record<string,unknown>>>([]);const [error,setError]=useState('')
 useEffect(()=>{api<Array<Record<string,unknown>>>('/api/platform/audit-logs?limit=200',token).then(setRows).catch(e=>setError(e instanceof Error?e.message:'Could not load audit logs.'))},[token])
 return <section className="surface-card page-table-card"><div className="surface-heading"><div><h2>Security and administrative audit</h2><p>Recent account, document, application, plan and promotion actions.</p></div><Pill>{rows.length} events</Pill></div>{error&&<div className="alert alert-error">{error}</div>}<div className="table-scroll"><table className="data-table"><thead><tr><th>TIME</th><th>ACTOR</th><th>ACTION</th><th>TARGET</th><th>OUTCOME</th></tr></thead><tbody>{rows.map((r,i)=><tr key={String(r.id??i)}><td>{dateLabel(String(r.createdAt??''))}</td><td>{String(r.actorUsername??'system')}<small>{String(r.actorRole??'')}</small></td><td>{pretty(String(r.action??''))}</td><td>{String(r.targetType??'')} · {String(r.targetId??'')}</td><td><Pill tone={r.outcome==='SUCCESS'?'green':'red'}>{String(r.outcome??'')}</Pill></td></tr>)}</tbody></table></div></section>
}

function Metric({ label, value, icon, accent, note }: { label: string; value: string | number; icon: string; accent: string; note: string }) { return <article className="metric-card-v1"><div className={`metric-icon-v1 metric-${accent}`}>{icon}</div><span>{label}</span><strong>{value}</strong><small>{note}</small></article> }
function HealthRow({ name, detail, warn = false }: { name: string; detail: string; warn?: boolean }) { return <div className="health-row"><span className={warn ? 'health-status health-warn' : 'health-status'}>{warn ? '!' : '✓'}</span><span><b>{name}</b><small>{detail}</small></span><i className={warn ? 'health-indicator warn' : 'health-indicator'}/></div> }
function BusinessTable({ businesses, loading, onStatus, full = false }: { businesses: Business[]; loading: boolean; onStatus: (id: string, status: string) => void; full?: boolean }) {
  if (loading) return <div className="table-message">Loading platform records…</div>
  if (!businesses.length) return <EmptyState title="No stores have joined yet" text="When a business creates a store, it will appear here."/>
  return <div className="table-scroll"><table className="data-table"><thead><tr><th>BUSINESS</th><th>OWNER</th><th>PLAN</th><th>STATUS</th><th>CREATED</th>{full && <th>STORE LINK</th>}</tr></thead><tbody>{businesses.map(b => <tr key={b.id}><td><div className="table-business"><span className="business-avatar">{initials(b.name)}</span><span><b>{b.name}</b><small>{b.ownerEmail ?? b.phone ?? b.slug}</small></span></div></td><td>{b.ownerName}</td><td><Pill>{pretty(b.plan)}</Pill></td><td><select className={`status-select status-${b.status.toLowerCase()}`} value={b.status} aria-label={`Status of ${b.name}`} onChange={e => onStatus(b.id, e.target.value)}>{b.status !== 'PENDING' && <option value="ACTIVE">Active</option>}<option value="PENDING">Pending</option><option value="SUSPENDED">Suspended</option></select></td><td>{dateLabel(b.createdAt)}</td>{full && <td><button className="table-link" onClick={() => navigate(`/store/${b.slug}`)}>/store/{b.slug} ↗</button></td>}</tr>)}</tbody></table></div>
}

const emptyProduct: ProductInput = { name: '', sku: '', category: 'General', description: '', retailPrice: '', wholesalePrice: '', wholesaleMinQuantity: '5', imageUrl: '', paymentPolicy: 'COD_ALLOWED', advancePaymentPercent: '30', variantsText: '' }
function variantText(variants: ProductVariant[] = []) { return variants.map(v => `${v.label} | ${v.sku ?? ''} | ${v.retailPrice ?? ''} | ${v.wholesalePrice ?? ''}`).join('\n') }
function parseVariantText(text: string, existing: ProductVariant[] = []) {
  return text.split('\n').map(line => line.trim()).filter(Boolean).map(line => {
    const [label = '', sku = '', retail = '', wholesale = ''] = line.split('|').map(part => part.trim())
    const old = existing.find(v => v.label.toLocaleLowerCase() === label.toLocaleLowerCase())
    return { id: old?.id, label, sku: sku || null, retailPrice: retail ? Number(retail) : null, wholesalePrice: wholesale ? Number(wholesale) : null, active: true }
  })
}
function MerchantDashboard({ token, user, onLogout }: { token: string; user: AuthUser; onLogout: () => void }) {
  const [activeTab, setActiveTab] = useState('overview')
  const [store, setStore] = useState<Store | null>(null)
  const [products, setProducts] = useState<Product[]>([])
  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showProductForm, setShowProductForm] = useState(false)
  const [editingProductId, setEditingProductId] = useState<string | null>(null)
  const [productInput, setProductInput] = useState<ProductInput>(emptyProduct)
  const [saving, setSaving] = useState(false)
  const [storeInput, setStoreInput] = useState<StoreInput | null>(null)
  const [paymentSettings, setPaymentSettings] = useState<PaymentSettings | null>(null)
  const [success, setSuccess] = useState('')
  const [uploadingAsset, setUploadingAsset] = useState(false)
  async function refresh() {
    setLoading(true); setError('')
    try {
      const [s, p, o, payments] = await Promise.all([api<Store>('/api/merchant/store', token), api<Product[]>('/api/merchant/products', token), api<Order[]>('/api/merchant/orders', token), api<PaymentSettings>('/api/merchant/payment-providers', token)])
      setStore(s); setProducts(p); setOrders(o); setPaymentSettings(payments); setStoreInput({ name: s.name, phone: s.phone ?? '', description: s.description ?? '', templateKey: s.templateKey || 'classic', primaryColor: s.primaryColor || '#2f6f5e', codEnabled: s.codEnabled, onlinePaymentEnabled: s.onlinePaymentEnabled, advancePaymentPercent: String(s.advancePaymentPercent), deliveryFee: String(s.deliveryFee), minimumOrderAmount: String(s.minimumOrderAmount) })
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to load your workspace.') }
    finally { setLoading(false) }
  }
  // Fetch tenant data once the authenticated session is available.
  // eslint-disable-next-line react-hooks/set-state-in-effect, react-hooks/exhaustive-deps
  useEffect(() => { void refresh() }, [token])
  const activeProducts = products.filter(p => p.active)
  const pendingOrders = orders.filter(o => !['DELIVERED', 'CANCELLED'].includes(o.status))
  async function createProduct(e: FormEvent) {
    e.preventDefault(); setSaving(true); setError(''); setSuccess('')
    try {
      const existingVariants = editingProductId ? products.find(p => p.id === editingProductId)?.variants ?? [] : []
      const { variantsText, ...fields } = productInput
      const payload = { ...fields, retailPrice: Number(productInput.retailPrice), wholesalePrice: Number(productInput.wholesalePrice), wholesaleMinQuantity: Number(productInput.wholesaleMinQuantity), advancePaymentPercent: Number(productInput.advancePaymentPercent), variants: parseVariantText(variantsText, existingVariants) }
      if (editingProductId) { await api(`/api/merchant/products/${editingProductId}`, token, { method: 'PATCH', body: JSON.stringify(payload) }); setSuccess('Product changes saved.') }
      else { await api('/api/merchant/products', token, { method: 'POST', body: JSON.stringify(payload) }); setSuccess('Product added to your catalog.') }
      setProductInput(emptyProduct); setEditingProductId(null); setShowProductForm(false); await refresh()
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not save product.') }
    finally { setSaving(false) }
  }
  function editProduct(product: Product) {
    setEditingProductId(product.id)
    setProductInput({ name: product.name, sku: product.sku ?? '', category: product.category, description: product.description ?? '', retailPrice: String(product.retailPrice), wholesalePrice: String(product.wholesalePrice), wholesaleMinQuantity: String(product.wholesaleMinQuantity), imageUrl: product.imageUrl ?? '', paymentPolicy: product.paymentPolicy, advancePaymentPercent: String(product.advancePaymentPercent), variantsText: variantText(product.variants) })
    setShowProductForm(true)
  }
  async function hideProduct(id: string) {
    setError(''); setSuccess('')
    try { await api(`/api/merchant/products/${id}`, token, { method: 'DELETE' }); setSuccess('Product removed from your public storefront.'); await refresh() }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not update product.') }
  }
  async function updateOrder(id: string, status: string) {
    setError(''); setSuccess('')
    try { await api(`/api/merchant/orders/${id}/status`, token, { method: 'PATCH', body: JSON.stringify({ status }) }); setSuccess('Order status updated.'); await refresh() }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not update order.') }
  }
  async function updatePayment(id: string) {
    setError(''); setSuccess('')
    try { await api(`/api/merchant/orders/${id}/payment-status`, token, { method: 'PATCH', body: JSON.stringify({ status: 'PAID' }) }); setSuccess('Cash-on-delivery payment marked received.'); await refresh() }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not update payment status.') }
  }
  async function uploadAsset(assetType: 'PRODUCT_IMAGE' | 'STORE_LOGO', file: File) {
    setUploadingAsset(true); setError(''); setSuccess('')
    try { const form = new FormData(); form.append('assetType', assetType); form.append('file', file); const result = await api<AssetResponse>('/api/tenant/assets', token, { method: 'POST', body: form }); const url = `${API_BASE_URL}${result.url}`; if (assetType === 'PRODUCT_IMAGE') setProductInput(old => ({ ...old, imageUrl: url })); else { setSuccess('Store logo uploaded.'); await refresh() } }
    catch (e) { setError(e instanceof Error ? e.message : 'Image upload failed.') }
    finally { setUploadingAsset(false) }
  }
  async function saveSettings(e: FormEvent) {
    e.preventDefault(); if (!storeInput) return; setSaving(true); setError(''); setSuccess('')
    try { const updated = await api<Store>('/api/merchant/store', token, { method: 'PATCH', body: JSON.stringify({ ...storeInput, advancePaymentPercent: Number(storeInput.advancePaymentPercent), deliveryFee: Number(storeInput.deliveryFee), minimumOrderAmount: Number(storeInput.minimumOrderAmount) }) }); setStore(updated); setSuccess('Store settings saved.'); await refresh() }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not save store settings.') }
    finally { setSaving(false) }
  }
  return <DashboardFrame user={user} title={activeTab === 'products' ? 'Products' : activeTab === 'orders' ? 'Orders' : activeTab === 'billing' ? 'Plan & billing' : activeTab === 'staff' ? 'Staff & access' : activeTab === 'store' ? 'Store settings' : 'Your workspace'} eyebrow="YOUR BUSINESS WORKSPACE" active={activeTab} onTab={setActiveTab} onLogout={onLogout}>
    {error && <div className="alert alert-error workspace-alert">{error}<button onClick={() => setError('')}>×</button></div>}{success && <div className="alert alert-success workspace-alert">✓ {success}<button onClick={() => setSuccess('')}>×</button></div>}
    {loading && !store ? <div className="surface-card table-message">Loading your store…</div> : null}
    {activeTab === 'overview' && store && <>
      <div className="welcome-banner merchant-welcome"><div><Pill tone="green">YOUR STOREFRONT IS READY</Pill><h2>Good work starts <em>right here.</em></h2><p>Manage your catalog, track orders, and keep your shop information up to date.</p><div className="banner-cta-row"><Button onClick={() => setActiveTab('products')}>Add your first product <Icon>＋</Icon></Button><button className="banner-text-action" onClick={() => navigate(`/store/${store.slug}`)}>Open public store ↗</button></div></div><div className="banner-art"><span className="banner-ring ring-a"/><span className="banner-ring ring-b"/><span className="banner-monogram">{initials(store.name)}</span><span className="banner-spark">✳</span></div></div>
      <div className="metric-grid"><Metric label="Listed products" value={activeProducts.length} icon="▦" accent="mint" note="Visible on your storefront"/><Metric label="Orders to fulfil" value={pendingOrders.length} icon="↗" accent="blue" note="Not delivered or cancelled"/><Metric label="All orders" value={orders.length} icon="◷" accent="lilac" note="Order history"/><Metric label="Listed price value" value={money(activeProducts.reduce((a, p) => a + p.retailPrice, 0))} icon="रु" accent="sand" note="Sum of product retail prices"/></div>
      <section className="dashboard-columns"><article className="surface-card"><div className="surface-heading"><div><h2>Recent orders</h2><p>Keep customer orders moving</p></div><button className="subtle-link" onClick={() => setActiveTab('orders')}>All orders ↗</button></div>{orders.length ? <OrderTable orders={orders.slice(0, 5)} onStatus={updateOrder} onPaymentReceived={updatePayment}/> : <EmptyState title="No orders yet" text="Orders placed through your store will appear here." action={<Button variant="secondary" onClick={() => navigate(`/store/${store.slug}`)}>Preview storefront</Button>}/>}</article><aside className="surface-card operations-card"><div className="surface-heading"><div><h2>Store checklist</h2><p>Good next steps</p></div></div><div className="checklist"><div className={activeProducts.length ? 'checklist-done' : ''}><span>{activeProducts.length ? '✓' : '1'}</span><div><b>Add products</b><small>{activeProducts.length ? `${activeProducts.length} listed product(s)` : 'Add your first product to start selling'}</small></div><button onClick={() => setActiveTab('products')}>→</button></div><div className={store.description ? 'checklist-done' : ''}><span>{store.description ? '✓' : '2'}</span><div><b>Describe your store</b><small>{store.description ? 'Store description is set' : 'Tell customers what makes you different'}</small></div><button onClick={() => setActiveTab('store')}>→</button></div><div className={store.codEnabled ? 'checklist-done' : ''}><span>{store.codEnabled ? '✓' : '3'}</span><div><b>Review checkout rules</b><small>{store.codEnabled ? 'Cash on delivery is enabled' : 'Review your payment options'}</small></div><button onClick={() => setActiveTab('store')}>→</button></div></div><div className="notice-note"><span>i</span><p>Online checkout is available after connecting and validating a merchant account.</p></div></aside></section>
    </>}
    {activeTab === 'products' && <section className="surface-card page-table-card"><div className="surface-heading"><div><h2>Your catalog</h2><p>Manage retail/wholesale prices and payment rules per product.</p></div><Button onClick={() => { setShowProductForm(v => !v); setEditingProductId(null); setProductInput(emptyProduct) }}><Icon>＋</Icon> Add product</Button></div>{showProductForm && <form className="product-form" onSubmit={createProduct}><div className="product-form-heading"><div><b>{editingProductId ? 'Edit product' : 'New product'}</b><p>Fields marked required are needed to publish.</p></div><button type="button" onClick={() => { setShowProductForm(false); setEditingProductId(null); setProductInput(emptyProduct) }} aria-label="Close form">×</button></div><div className="form-grid"><label>Product name<input value={productInput.name} onChange={e => setProductInput({ ...productInput, name: e.target.value })} required maxLength={140}/></label><label>Category<input value={productInput.category} onChange={e => setProductInput({ ...productInput, category: e.target.value })} required maxLength={80}/></label><label>SKU <small>(optional)</small><input value={productInput.sku} onChange={e => setProductInput({ ...productInput, sku: e.target.value })} maxLength={80}/></label><label>Retail price (NPR)<input type="number" min="0.01" step="0.01" value={productInput.retailPrice} onChange={e => setProductInput({ ...productInput, retailPrice: e.target.value })} required/></label><label>Wholesale price (NPR)<input type="number" min="0.01" step="0.01" value={productInput.wholesalePrice} onChange={e => setProductInput({ ...productInput, wholesalePrice: e.target.value })} required/></label><label>Wholesale minimum quantity<input type="number" min="2" max="10000" value={productInput.wholesaleMinQuantity} onChange={e => setProductInput({ ...productInput, wholesaleMinQuantity: e.target.value })} required/></label><label>Payment rule<select value={productInput.paymentPolicy} onChange={e => setProductInput({ ...productInput, paymentPolicy: e.target.value as PaymentPolicy })}><option value="COD_ALLOWED">Cash on delivery allowed</option><option value="ADVANCE_REQUIRED">Advance payment + COD balance</option><option value="ONLINE_ONLY" disabled={!paymentSettings?.esewa.enabled && !paymentSettings?.khalti.enabled}>Online payment only</option></select></label><label>Advance (%)<input type="number" min="0" max="100" value={productInput.advancePaymentPercent} onChange={e => setProductInput({ ...productInput, advancePaymentPercent: e.target.value })} disabled={productInput.paymentPolicy !== 'ADVANCE_REQUIRED'}/></label><label className="form-full">Description<textarea rows={3} value={productInput.description} onChange={e => setProductInput({ ...productInput, description: e.target.value })} maxLength={3000}/></label><label className="form-full">Product variants <small>(optional)</small><textarea rows={4} value={productInput.variantsText} onChange={e => setProductInput({ ...productInput, variantsText: e.target.value })} placeholder={"Red / Small | SKU-RS | 1200 | 1000\nBlue / Large | | 1500 | 1250"}/><small className="field-help">One per line: name | optional SKU | optional retail price | optional wholesale price. Blank prices use the product price.</small></label><label className="form-full">Product photo <small>(optional · JPEG/PNG, up to 5 MB · stored in PostgreSQL)</small><input type="file" accept="image/jpeg,image/png" disabled={uploadingAsset} onChange={e => { const file=e.target.files?.[0]; if(file) void uploadAsset('PRODUCT_IMAGE',file) }}/><small className="field-help">{uploadingAsset ? 'Uploading image…' : productInput.imageUrl ? 'Photo uploaded and ready to save.' : 'Choose a photo to upload.'}</small></label></div><div className="product-form-actions"><span>Inventory quantities are not tracked; customers can still select quantities for each order.</span><Button type="submit" disabled={saving}>{saving ? 'Saving…' : editingProductId ? 'Save changes' : 'Publish product'} <Icon>↗</Icon></Button></div></form>}{products.length ? <div className="product-list">{products.map(p => <ProductRow key={p.id} product={p} onEdit={editProduct} onHide={hideProduct}/>)}</div> : <EmptyState title="Your catalog is waiting" text="Add products to publish them to your storefront." action={<Button onClick={() => setShowProductForm(true)}>Add a product</Button>}/>}</section>}
    {activeTab === 'staff' && user.role === 'STORE_OWNER' && <StaffPanel token={token}/>}{activeTab === 'orders' && <section className="surface-card page-table-card"><div className="surface-heading"><div><h2>Customer orders</h2><p>Review orders and update fulfilment status.</p></div><Pill>{orders.length} total</Pill></div>{orders.length ? <OrderTable orders={orders} onStatus={updateOrder} onPaymentReceived={updatePayment} detailed/> : <EmptyState title="No orders have arrived" text="Once a customer checks out from your storefront, their order will be listed here."/>}</section>}
    {activeTab === 'billing' && user.role === 'STORE_OWNER' && <TenantBillingPanel token={token}/>}
    {activeTab === 'store' && storeInput && <section className="surface-card settings-card"><div className="surface-heading"><div><h2>Storefront settings</h2><p>Manage customer-facing details and checkout rules.</p></div><Button variant="secondary" onClick={() => navigate(`/store/${store?.slug}`)}>Preview store ↗</Button></div><form onSubmit={saveSettings}><div className="settings-section"><span className="settings-section-number">01</span><div className="settings-section-content"><h3>Business profile</h3><p>These details appear on your public storefront.</p><div className="form-grid"><label>Store name<input value={storeInput.name} onChange={e => setStoreInput({ ...storeInput, name: e.target.value })} required maxLength={120}/></label><label>Contact phone<input value={storeInput.phone} onChange={e => setStoreInput({ ...storeInput, phone: e.target.value })} maxLength={30}/></label><label className="form-full">Store description<textarea rows={3} value={storeInput.description} onChange={e => setStoreInput({ ...storeInput, description: e.target.value })} maxLength={1000} placeholder="What do you sell? What should customers know?"/></label><label className="form-full">Store logo <small>(optional · JPEG/PNG, up to 5 MB · stored in PostgreSQL)</small><input type="file" accept="image/jpeg,image/png" disabled={uploadingAsset} onChange={e => { const file=e.target.files?.[0]; if(file) void uploadAsset('STORE_LOGO',file) }}/><small className="field-help">{uploadingAsset ? 'Uploading logo…' : store?.logoUrl ? 'A store logo is configured.' : 'Upload a logo to show it on your storefront.'}</small></label></div></div></div><div className="settings-section"><span className="settings-section-number">02</span><div className="settings-section-content"><h3>Store experience</h3><p>The storefront currently uses one fixed, responsive layout. Additional templates can be introduced later.</p><div className="template-choice"><div className="template-preview"><div/><i/><i/><i/></div><div><b>Classic storefront</b><small>Fixed layout · Mobile responsive</small></div><Pill tone="green">Active</Pill></div><div className="form-grid settings-colors"><label>Accent colour<input type="color" value={storeInput.primaryColor} onChange={e => setStoreInput({ ...storeInput, primaryColor: e.target.value })}/></label><label>Minimum order amount (NPR)<input type="number" min="0" step="0.01" value={storeInput.minimumOrderAmount} onChange={e => setStoreInput({ ...storeInput, minimumOrderAmount: e.target.value })} required/></label></div></div></div><div className="settings-section"><span className="settings-section-number">03</span><div className="settings-section-content"><h3>Checkout & payment rules</h3><p>Choose what the store offers. Product-level rules still apply at checkout.</p><div className="toggle-setting"><span className="toggle-copy"><b>Cash on delivery</b><small>Customers pay when their order is delivered.</small></span><label className="toggle"><input type="checkbox" checked={storeInput.codEnabled} onChange={e => setStoreInput({ ...storeInput, codEnabled: e.target.checked })} aria-label="Enable cash on delivery"/><span/></label></div><div className="toggle-setting"><span className="toggle-copy"><b>Online payment option</b><small>Available after connecting and enabling an eSewa or Khalti merchant account.</small></span><label className="toggle"><input type="checkbox" checked={storeInput.onlinePaymentEnabled} disabled={!paymentSettings?.esewa.enabled && !paymentSettings?.khalti.enabled} onChange={e => setStoreInput({ ...storeInput, onlinePaymentEnabled: e.target.checked })} aria-label="Enable online payments"/><span/></label></div><div className="form-grid"><label>Default advance payment (%)<input type="number" min="0" max="100" value={storeInput.advancePaymentPercent} onChange={e => setStoreInput({ ...storeInput, advancePaymentPercent: e.target.value })} required/></label><label>Delivery fee (NPR)<input type="number" min="0" step="0.01" value={storeInput.deliveryFee} onChange={e => setStoreInput({ ...storeInput, deliveryFee: e.target.value })} required/></label></div><div className="notice-note settings-warning"><span>!</span><p>Gateway credentials are encrypted at rest. Use sandbox credentials until each merchant account completes a real end-to-end verification.</p></div></div></div><div className="settings-save"><span>Changes apply to your public storefront.</span><Button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save settings'} <Icon>✓</Icon></Button></div></form></section>}
    {activeTab === 'store' && <PaymentProviderSettingsPanel token={token} settings={paymentSettings} onSaved={refresh}/>}
  </DashboardFrame>
}
function PaymentProviderSettingsPanel({ token, settings, onSaved }: { token: string; settings: PaymentSettings | null; onSaved: () => Promise<void> }) {
  const [esewaMerchantCode, setEsewaMerchantCode] = useState('')
  const [esewaSecret, setEsewaSecret] = useState('')
  const [khaltiSecret, setKhaltiSecret] = useState('')
  const [esewaSandbox, setEsewaSandbox] = useState(settings?.esewa.sandbox ?? true)
  const [khaltiSandbox, setKhaltiSandbox] = useState(settings?.khalti.sandbox ?? true)
  const [esewaEnabled, setEsewaEnabled] = useState(settings?.esewa.enabled ?? false)
  const [khaltiEnabled, setKhaltiEnabled] = useState(settings?.khalti.enabled ?? false)
  const [savingProvider, setSavingProvider] = useState<PaymentProvider | null>(null)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  async function save(provider: PaymentProvider) {
    const esewa = provider === 'ESEWA'
    setSavingProvider(provider); setError(''); setSuccess('')
    try {
      await api<PaymentSettings>(`/api/merchant/payment-providers/${provider}`, token, {
        method: 'PUT',
        body: JSON.stringify({
          merchantCode: esewa ? esewaMerchantCode : '',
          secretKey: esewa ? esewaSecret : khaltiSecret,
          sandbox: esewa ? esewaSandbox : khaltiSandbox,
          enabled: esewa ? esewaEnabled : khaltiEnabled,
        }),
      })
      if (esewa) { setEsewaSecret(''); setSuccess('eSewa merchant connection saved.') }
      else { setKhaltiSecret(''); setSuccess('Khalti merchant connection saved.') }
      await onSaved()
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not save merchant connection.') }
    finally { setSavingProvider(null) }
  }
  return <section className="surface-card settings-card"><div className="surface-heading"><div><h2>Merchant payment accounts</h2><p>Each provider uses your own merchant account, so customer payments settle directly to your business.</p></div><Pill tone={settings?.onlinePaymentEnabled ? 'green' : 'amber'}>{settings?.onlinePaymentEnabled ? 'Online checkout enabled' : 'Online checkout off'}</Pill></div>
    {error && <div className="alert alert-error workspace-alert">{error}<button onClick={() => setError('')}>×</button></div>}{success && <div className="alert alert-success workspace-alert">✓ {success}<button onClick={() => setSuccess('')}>×</button></div>}
    <div className="settings-section"><span className="settings-section-number">E</span><div className="settings-section-content"><h3>eSewa</h3><p>{settings?.esewa.configured ? `Connected · ${settings.esewa.sandbox ? 'Sandbox' : 'Production'} mode` : 'Connect the product code and secret key issued for your eSewa merchant account.'}</p><div className="form-grid"><label>Merchant product code<input value={esewaMerchantCode} onChange={e => setEsewaMerchantCode(e.target.value)} maxLength={100} placeholder={settings?.esewa.configured ? 'Leave blank to keep the saved code' : 'Product code'} autoComplete="off"/></label><label>Merchant secret key<input type="password" value={esewaSecret} onChange={e => setEsewaSecret(e.target.value)} maxLength={500} placeholder={settings?.esewa.configured ? 'Leave blank to keep the saved key' : 'Secret key'} autoComplete="new-password"/></label></div><div className="toggle-setting"><span className="toggle-copy"><b>Use sandbox</b><small>Use the test account and eSewa UAT endpoint until verified.</small></span><label className="toggle"><input type="checkbox" checked={esewaSandbox} onChange={e => setEsewaSandbox(e.target.checked)}/><span/></label></div><div className="toggle-setting"><span className="toggle-copy"><b>Enable eSewa at checkout</b><small>Customers can select this provider after online checkout is enabled in store settings.</small></span><label className="toggle"><input type="checkbox" checked={esewaEnabled} onChange={e => setEsewaEnabled(e.target.checked)}/><span/></label></div><Button type="button" variant="secondary" disabled={savingProvider !== null} onClick={() => void save('ESEWA')}>{savingProvider === 'ESEWA' ? 'Saving…' : 'Save eSewa connection'}</Button></div></div>
    <div className="settings-section"><span className="settings-section-number">K</span><div className="settings-section-content"><h3>Khalti</h3><p>{settings?.khalti.configured ? `Connected · ${settings.khalti.sandbox ? 'Sandbox' : 'Production'} mode` : 'Connect the secret key issued for your Khalti merchant account.'}</p><label>Merchant secret key<input type="password" value={khaltiSecret} onChange={e => setKhaltiSecret(e.target.value)} maxLength={500} placeholder={settings?.khalti.configured ? 'Leave blank to keep the saved key' : 'Secret key'} autoComplete="new-password"/></label><div className="toggle-setting"><span className="toggle-copy"><b>Use sandbox</b><small>Use Khalti test credentials until the flow is verified.</small></span><label className="toggle"><input type="checkbox" checked={khaltiSandbox} onChange={e => setKhaltiSandbox(e.target.checked)}/><span/></label></div><div className="toggle-setting"><span className="toggle-copy"><b>Enable Khalti at checkout</b><small>Customers can select this provider after online checkout is enabled in store settings.</small></span><label className="toggle"><input type="checkbox" checked={khaltiEnabled} onChange={e => setKhaltiEnabled(e.target.checked)}/><span/></label></div><Button type="button" variant="secondary" disabled={savingProvider !== null} onClick={() => void save('KHALTI')}>{savingProvider === 'KHALTI' ? 'Saving…' : 'Save Khalti connection'}</Button></div></div>
    <p className="field-help">Secret keys are encrypted before storage and are never returned to the browser. For production, use the provider credentials that belong to this tenant.</p>
  </section>
}

function ProductRow({ product, onEdit, onHide }: { product: Product; onEdit: (product: Product) => void; onHide: (id: string) => void }) { return <article className={`product-row ${!product.active ? 'product-row-muted' : ''}`}><div className="product-row-art">{product.imageUrl ? <img src={product.imageUrl} alt="" onError={e => { e.currentTarget.style.display = 'none' }}/> : <span>{productEmoji(product.category)}</span>}</div><div className="product-row-main"><b>{product.name}</b><small>{product.category}{product.sku ? ` · ${product.sku}` : ''}</small><p>{product.description || 'No description added yet.'}</p><div className="product-row-tags"><Pill tone={product.paymentPolicy === 'ONLINE_ONLY' ? 'blue' : product.paymentPolicy === 'ADVANCE_REQUIRED' ? 'amber' : 'green'}>{pretty(product.paymentPolicy)}</Pill><Pill>{`Wholesale from ${product.wholesaleMinQuantity} units`}</Pill>{!product.active && <Pill tone="red">Hidden</Pill>}</div></div><div className="product-row-prices"><b>{money(product.retailPrice)}</b><small>Retail</small><b>{money(product.wholesalePrice)}</b><small>Wholesale</small></div>{product.active && <div className="product-row-actions"><button onClick={() => onEdit(product)}>Edit</button><button onClick={() => onHide(product.id)}>Hide</button></div>}</article> }
function OrderTable({ orders, onStatus, onPaymentReceived, detailed = false }: { orders: Order[]; onStatus: (id: string, status: string) => void; onPaymentReceived?: (id: string) => void; detailed?: boolean }) { return <div className="table-scroll"><table className="data-table order-table"><thead><tr><th>ORDER</th><th>CUSTOMER</th><th>PAYMENT</th><th>TOTAL</th><th>STATUS</th>{detailed && <th>PLACED</th>}</tr></thead><tbody>{orders.map(o => <tr key={o.id}><td><b className="order-number">{o.orderNumber}</b><small className="table-subline">{o.items.reduce((n, i) => n + i.quantity, 0)} item(s)</small></td><td><span className="table-customer"><b>{o.customerName}</b><small>{o.phone}</small></span></td><td><Pill tone={o.paymentStatus === 'PAID' ? 'green' : o.paymentStatus === 'PAYMENT_PENDING' ? 'amber' : 'neutral'}>{pretty(o.paymentMethod)}</Pill><small className="table-subline">{pretty(o.paymentStatus)}</small>{onPaymentReceived && (o.paymentMethod === 'COD' && o.status === 'DELIVERED' || o.paymentMethod === 'ADVANCE_COD' && o.paymentStatus === 'PARTIALLY_PAID' && (o.status === 'SHIPPED' || o.status === 'DELIVERED')) && o.paymentStatus !== 'PAID' && o.paymentStatus !== 'REFUNDED' && <button className="mark-payment-button" onClick={() => onPaymentReceived(o.id)}>{o.paymentMethod === 'ADVANCE_COD' ? 'Record balance received' : 'Record received'}</button>}</td><td><b>{money(o.totalAmount)}</b>{o.advanceAmount > 0 && <small className="table-subline">Advance {money(o.advanceAmount)}</small>}</td><td><select className={`status-select status-${o.status.toLowerCase()}`} aria-label={`Order status ${o.orderNumber}`} value={o.status} onChange={e => onStatus(o.id, e.target.value)}><option value="PLACED">Placed</option><option value="CONFIRMED">Confirmed</option><option value="PROCESSING">Processing</option><option value="SHIPPED">Shipped</option><option value="DELIVERED">Delivered</option><option value="CANCELLED">Cancelled</option></select></td>{detailed && <td>{dateLabel(o.createdAt)}</td>}</tr>)}</tbody></table></div> }

function Storefront({ slug }: { slug: string | null }) {
  const [store, setStore] = useState<Store | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [cart, setCart] = useState<CartLine[]>([])
  const [variantSelection, setVariantSelection] = useState<Record<string, string>>({})
  const [showCheckout, setShowCheckout] = useState(false)
  const [salesType, setSalesType] = useState<'RETAIL' | 'WHOLESALE'>('RETAIL')
  const [paymentMethod, setPaymentMethod] = useState<'COD' | 'ONLINE' | 'ADVANCE_COD'>('COD')
  const [paymentProvider, setPaymentProvider] = useState<PaymentProvider>('ESEWA')
  const [customer, setCustomer] = useState({ customerName: '', phone: '', email: '', province: 'Koshi', district: '', municipality: '', ward: '', addressLine: '', customerNote: '' })
  const [placing, setPlacing] = useState(false)
  const [checkoutKey, setCheckoutKey] = useState<string | null>(null)
  const [checkoutError, setCheckoutError] = useState('')
  const [receipt, setReceipt] = useState<Order | null>(null)
  useEffect(() => {
    let alive = true
    const endpoint = slug ? `/api/store/${encodeURIComponent(slug)}` : '/api/storefront/current'
    api<Store>(endpoint).then(d => {
      if (!alive) return
      setStore(d); setError('')
      try {
        const saved = sessionStorage.getItem(`byawasaya-checkout:${d.slug}`)
        if (saved) {
          const snapshot = JSON.parse(saved) as { cart?: CartLine[]; customer?: typeof customer; checkoutKey?: string | null }
          if (Array.isArray(snapshot.cart)) setCart(snapshot.cart)
          if (snapshot.customer) setCustomer(snapshot.customer)
          if (snapshot.checkoutKey) setCheckoutKey(snapshot.checkoutKey)
        }
      } catch { /* Ignore malformed stale checkout recovery data. */ }
    }).catch(e => { if (alive) setError(e instanceof Error ? e.message : 'Store could not be loaded.') }).finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [slug])
  const products = store?.products ?? []
  const subtotal = useMemo(() => cart.reduce((total, line) => { const retail = line.variant?.retailPrice ?? line.product.retailPrice; const wholesale = line.variant?.wholesalePrice ?? line.product.wholesalePrice; const unit = salesType === 'WHOLESALE' && line.quantity >= line.product.wholesaleMinQuantity ? wholesale : retail; return total + unit * line.quantity }, 0), [cart, salesType])
  const hasOnlineOnly = cart.some(l => l.product.paymentPolicy === 'ONLINE_ONLY')
  const hasAdvanceRequired = cart.some(l => l.product.paymentPolicy === 'ADVANCE_REQUIRED')
  const effectivePaymentMethod = hasOnlineOnly || (!store?.codEnabled && paymentMethod === 'COD') ? 'ONLINE' : hasAdvanceRequired ? (paymentMethod === 'ONLINE' ? 'ONLINE' : 'ADVANCE_COD') : (paymentMethod === 'ADVANCE_COD' ? 'COD' : paymentMethod)
  const availableProviders = store?.paymentProviders ?? []
  const selectedProvider = availableProviders.includes(paymentProvider) ? paymentProvider : availableProviders[0]
  const advancePercent = Math.max(store?.advancePaymentPercent ?? 0, ...cart.filter(line => line.product.paymentPolicy === 'ADVANCE_REQUIRED').map(line => line.product.advancePaymentPercent))
  const advanceDue = effectivePaymentMethod === 'ADVANCE_COD' ? (subtotal + (store?.deliveryFee ?? 0)) * advancePercent / 100 : 0
  useEffect(() => {
    if (!store || !showCheckout) return
    try { sessionStorage.setItem(`byawasaya-checkout:${store.slug}`, JSON.stringify({ cart, customer, checkoutKey })) }
    catch { /* Session storage is best-effort; checkout still works without it. */ }
  }, [store, showCheckout, cart, customer, checkoutKey])
  useEffect(() => {
    if (!showCheckout) return
    const dialog = document.querySelector<HTMLElement>('.checkout-modal')
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const first = dialog?.querySelector<HTMLElement>('input, select, textarea, button')
    first?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { setShowCheckout(false); return }
      if (event.key !== 'Tab' || !dialog) return
      const focusable = Array.from(dialog.querySelectorAll<HTMLElement>('a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'))
      if (!focusable.length) return
      const firstElement = focusable[0]
      const lastElement = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === firstElement) { event.preventDefault(); lastElement.focus() }
      else if (!event.shiftKey && document.activeElement === lastElement) { event.preventDefault(); firstElement.focus() }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => { document.removeEventListener('keydown', onKeyDown); previous?.focus() }
  }, [showCheckout])
  function addToCart(product: Product, variant?: ProductVariant) { setCart(old => { const existing = old.find(l => l.product.id === product.id && l.variant?.id === variant?.id); return existing ? old.map(l => l.product.id === product.id && l.variant?.id === variant?.id ? { ...l, quantity: Math.min(999, l.quantity + 1) } : l) : [...old, { product, variant, quantity: 1 }] }) }
  function setQuantity(id: string, variantId: string | undefined, quantity: number) { if (quantity < 1) { setCart(old => old.filter(l => l.product.id !== id || l.variant?.id !== variantId)); return } setCart(old => old.map(l => l.product.id === id && l.variant?.id === variantId ? { ...l, quantity: Math.min(999, quantity) } : l)) }
  async function placeOrder(e: FormEvent) {
    e.preventDefault()
    if (!store || !cart.length) return
    if (effectivePaymentMethod !== 'COD' && !selectedProvider) { setCheckoutError('This store has not enabled an online payment provider.'); return }
    const key = checkoutKey ?? crypto.randomUUID()
    setCheckoutKey(key); setPlacing(true); setCheckoutError('')
    try {
      const result = await api<CheckoutResponse>(`/api/store/${encodeURIComponent(store.slug)}/orders`, null, {
        method: 'POST', headers: { 'Idempotency-Key': key },
        body: JSON.stringify({ ...customer, salesType, paymentMethod: effectivePaymentMethod,
          paymentProvider: effectivePaymentMethod === 'COD' ? null : selectedProvider,
          items: cart.map(l => ({ productId: l.product.id, variantId: l.variant?.id, quantity: l.quantity })) }),
      })
      if (result.redirectUrl && result.redirectMethod === 'POST') {
        sessionStorage.setItem(`byawasaya-checkout:${store.slug}`, JSON.stringify({ cart, customer, checkoutKey: key }))
        const form = document.createElement('form'); form.method = 'POST'; form.action = result.redirectUrl
        Object.entries(result.redirectFields ?? {}).forEach(([name, value]) => { const input = document.createElement('input'); input.type = 'hidden'; input.name = name; input.value = value; form.appendChild(input) })
        document.body.appendChild(form); form.submit(); return
      }
      if (result.redirectUrl) {
        sessionStorage.setItem(`byawasaya-checkout:${store.slug}`, JSON.stringify({ cart, customer, checkoutKey: key }))
        window.location.assign(result.redirectUrl); return
      }
      setReceipt(result.order); setShowCheckout(false); setCart([]); setCheckoutKey(null); sessionStorage.removeItem(`byawasaya-checkout:${store.slug}`)
    } catch (e) { setCheckoutError(e instanceof Error ? e.message : 'Could not place order.') }
    finally { setPlacing(false) }
  }
  if (loading) return <div className="store-loading"><span className="loading-mark">b.</span><p>Opening storefront…</p></div>
  if (error || !store) return <div className="store-error"><Logo/><EmptyState title="We couldn't find this store" text={error || 'Check the store link and try again.'} action={<Button onClick={() => navigate('/')}>Back to Byawasaya</Button>}/></div>
  return <div className="storefront" style={{ '--store-accent': store.primaryColor || '#2f6f5e' } as React.CSSProperties}>
    <div className="store-announcement">A little more local. A little more thoughtful. <span>·</span> Shop with confidence</div><header className="store-nav"><div className="store-nav-inner"><button className="store-brand" onClick={() => navigate('/')}>{store.logoUrl ? <img src={store.logoUrl.startsWith('http') ? store.logoUrl : `${API_BASE_URL}${store.logoUrl}`} alt=""/> : <span>{initials(store.name)}</span>}<b>{store.name}</b></button><div className="store-nav-right"><span className="store-nav-note">Independent business · Nepal</span><button className="store-account-link" onClick={() => navigate('/customer/orders')}>Customer account</button><button className="cart-button" onClick={() => setShowCheckout(true)}>Bag <span>{cart.reduce((n, l) => n + l.quantity, 0)}</span></button></div></div></header>
    <main><section className="store-hero"><div className="store-hero-copy"><Pill tone="green">A STORE ON BYAWASAYA</Pill><h1>{store.name}<br/><em>made for everyday.</em></h1><p>{store.description || `Thoughtful products from ${store.name}. Browse the collection and order directly from our store.`}</p><button className="store-hero-link" onClick={() => document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth' })}>Explore the collection <span>↓</span></button><div className="store-hero-points"><span>✓ Local business</span><span>✓ NPR pricing</span><span>✓ Simple checkout</span></div></div><div className="store-hero-art"><div className="hero-art-circle"/><div className="hero-art-tile tile-big"><span>✳</span><small>THOUGHTFULLY<br/>SELECTED</small></div><div className="hero-art-tile tile-small tile-small-one">🍵</div><div className="hero-art-tile tile-small tile-small-two">👜</div><div className="hero-art-stamp">GOOD<br/>THINGS<br/><b>LIVE HERE.</b></div></div></section>
      <section className="store-perks"><div><span>01</span><b>Carefully selected</b><small>Products we stand behind</small></div><div><span>02</span><b>Retail + wholesale</b><small>Buying for yourself or your shop</small></div><div><span>03</span><b>Clear checkout</b><small>Payment options shown up front</small></div></section>
      <section className="store-catalog" id="catalog">
        <div className="store-section-heading">
          <div><Pill>THE COLLECTION</Pill><h2>Find your <em>everyday favourite.</em></h2><p>Browse what's available from {store.name}.</p></div>
          <label className="sales-type-control"><span>Shopping for</span><select value={salesType} onChange={e => setSalesType(e.target.value as 'RETAIL' | 'WHOLESALE')}><option value="RETAIL">Personal / retail</option><option value="WHOLESALE">Wholesale</option></select></label>
        </div>
        {products.length ? <div className="store-product-grid">{products.filter(p => p.active && (!(p.variants ?? []).length || p.variants.some(v => v.active))).map((p, index) => {
          const activeVariants = (p.variants ?? []).filter(v => v.active)
          const selectedVariant = activeVariants.find(v => v.id === variantSelection[p.id]) ?? activeVariants[0]
          const retailPrice = selectedVariant?.retailPrice ?? p.retailPrice
          const wholesalePrice = selectedVariant?.wholesalePrice ?? p.wholesalePrice
          const displayedPrice = salesType === 'WHOLESALE' ? wholesalePrice : retailPrice
          const canAdd = activeVariants.length === 0 || Boolean(selectedVariant)
          return <article className="store-product-card" key={p.id}>
            <div className={`store-product-visual visual-${index % 5}`}>
              {p.imageUrl ? <img src={p.imageUrl} alt={p.name} onError={e => { e.currentTarget.style.display = 'none' }}/> : <span>{productEmoji(p.category)}</span>}
              <span className="product-category-tag">{p.category}</span>
              <button disabled={!canAdd} onClick={() => addToCart(p, selectedVariant)} aria-label={`Add ${p.name} to bag`}>＋</button>
            </div>
            <div className="store-product-details">
              <div className="store-product-title"><h3>{p.name}</h3><span>{money(displayedPrice)}</span></div>
              <p>{p.description || 'A thoughtful pick from our collection.'}</p>
              {activeVariants.length > 0 && <label className="variant-picker">Choose option<select value={selectedVariant?.id ?? ''} onChange={e => setVariantSelection(old => ({ ...old, [p.id]: e.target.value }))}>{activeVariants.map(v => <option key={v.id} value={v.id}>{v.label}</option>)}</select></label>}
              <div className="store-product-foot"><small>{salesType === 'WHOLESALE' ? `Wholesale from ${p.wholesaleMinQuantity} units` : `Wholesale ${money(wholesalePrice)} / ${p.wholesaleMinQuantity}+`}</small><button disabled={!canAdd} onClick={() => addToCart(p, selectedVariant)}>Add to bag <span>↗</span></button></div>
              {p.paymentPolicy !== 'COD_ALLOWED' && <div className="product-payment-notice">{p.paymentPolicy === 'ONLINE_ONLY' ? 'Online payment only' : `Advance ${p.advancePaymentPercent}% required`}</div>}
            </div>
          </article>
        })}</div> : <EmptyState title="Products coming soon" text="This store hasn't published a product yet. Check back soon."/>}
      </section>
      <section className="store-about"><div className="store-about-mark">✳</div><div><span>GOOD BUSINESS, LOCAL ROOTS</span><h2>Every order supports<br/><em>a real business.</em></h2><p>Byawasaya helps independent businesses show up online, reach customers, and run the work behind every order.</p></div><button onClick={() => navigate('/')}>About Byawasaya ↗</button></section>
    </main><footer className="store-footer"><button className="store-footer-brand" onClick={() => navigate('/')}><span className="brand-symbol">b<span>.</span></span> Powered by Byawasaya</button><span>{store.name} · Nepal</span><span>All prices in NPR</span></footer>
    {cart.length > 0 && !showCheckout && <button className="floating-cart" onClick={() => setShowCheckout(true)}><span>Bag · {cart.reduce((n, l) => n + l.quantity, 0)} item(s)</span><b>{money(subtotal + (store.deliveryFee ?? 0))}</b><span>Checkout ↗</span></button>}
    {showCheckout && <div className="modal-backdrop" role="presentation" onClick={e => { if (e.target === e.currentTarget) setShowCheckout(false) }}><section className="checkout-modal" role="dialog" aria-modal="true" aria-label="Checkout"><header className="checkout-header"><div><Pill>YOUR ORDER</Pill><h2>Checkout</h2><p>{store.name}</p></div><button onClick={() => setShowCheckout(false)} aria-label="Close checkout">×</button></header>{cart.length ? <><div className="checkout-layout"><form className="checkout-form" onSubmit={placeOrder}><h3>Delivery details</h3><label>Full name<input value={customer.customerName} onChange={e => setCustomer({ ...customer, customerName: e.target.value })} required maxLength={120}/></label><div className="form-two-col"><label>Phone number<input value={customer.phone} onChange={e => setCustomer({ ...customer, phone: e.target.value })} required maxLength={30} placeholder="98XXXXXXXX"/></label><label>Email <small>(optional)</small><input type="email" value={customer.email} onChange={e => setCustomer({ ...customer, email: e.target.value })}/></label></div><div className="form-two-col"><label>Province<input value={customer.province} onChange={e => setCustomer({ ...customer, province: e.target.value })} required maxLength={100}/></label><label>District<input value={customer.district} onChange={e => setCustomer({ ...customer, district: e.target.value })} required maxLength={100} placeholder="e.g. Sunsari"/></label></div><div className="form-two-col"><label>Municipality / city<input value={customer.municipality} onChange={e => setCustomer({ ...customer, municipality: e.target.value })} maxLength={120}/></label><label>Ward no.<input value={customer.ward} onChange={e => setCustomer({ ...customer, ward: e.target.value })} maxLength={10}/></label></div><label>Delivery address<input value={customer.addressLine} onChange={e => setCustomer({ ...customer, addressLine: e.target.value })} required maxLength={300} placeholder="Street, area, nearby landmark"/></label><label>Order note <small>(optional)</small><textarea rows={2} value={customer.customerNote} onChange={e => setCustomer({ ...customer, customerNote: e.target.value })} maxLength={500}/></label><h3 className="payment-heading">Payment method</h3><div className="payment-options">{!hasOnlineOnly && !hasAdvanceRequired && store.codEnabled && <label className={effectivePaymentMethod === 'COD' ? 'payment-option-selected' : ''}><input type="radio" name="payment" value="COD" checked={effectivePaymentMethod === 'COD'} onChange={() => setPaymentMethod('COD')}/><span><b>Cash on delivery</b><small>Pay when your order arrives</small></span><i>◉</i></label>}{hasAdvanceRequired && !hasOnlineOnly && store.codEnabled && store.onlinePaymentEnabled && availableProviders.length > 0 && <label className={effectivePaymentMethod === 'ADVANCE_COD' ? 'payment-option-selected' : ''}><input type="radio" name="payment" value="ADVANCE_COD" checked={effectivePaymentMethod === 'ADVANCE_COD'} onChange={() => setPaymentMethod('ADVANCE_COD')}/><span><b>Advance + cash on delivery</b><small>Pay the required advance online; pay the balance on delivery</small></span><i>◉</i></label>}{store.onlinePaymentEnabled && availableProviders.length > 0 && <label className={effectivePaymentMethod === 'ONLINE' ? 'payment-option-selected' : ''}><input type="radio" name="payment" value="ONLINE" checked={effectivePaymentMethod === 'ONLINE'} onChange={() => setPaymentMethod('ONLINE')}/><span><b>Online payment</b><small>Pay securely through this store's merchant account</small></span><i>◉</i></label>}</div>{(effectivePaymentMethod === 'ONLINE' || effectivePaymentMethod === 'ADVANCE_COD') && availableProviders.length > 0 && <div className="provider-options"><b>Choose a provider</b>{availableProviders.map(provider => <label key={provider}><input type="radio" name="paymentProvider" checked={selectedProvider === provider} onChange={() => setPaymentProvider(provider)}/><span>{provider === 'ESEWA' ? 'eSewa' : 'Khalti'}</span></label>)}</div>}{(hasOnlineOnly || hasAdvanceRequired || !store.codEnabled) && !store.onlinePaymentEnabled && <div className="alert alert-error">This basket requires an online payment provider, but the store has not enabled one. Contact the store.</div>}{checkoutError && <div className="alert alert-error">{checkoutError}</div>}<Button type="submit" className="button-wide" disabled={placing}>{placing ? 'Placing order…' : 'Place order'} <Icon>↗</Icon></Button></form><aside className="checkout-summary"><h3>Order summary</h3><div className="checkout-items">{cart.map(l => { const retail = l.variant?.retailPrice ?? l.product.retailPrice; const wholesale = l.variant?.wholesalePrice ?? l.product.wholesalePrice; const unit = salesType === 'WHOLESALE' && l.quantity >= l.product.wholesaleMinQuantity ? wholesale : retail; const variantKey = l.variant?.id ?? 'base'; return <div className="checkout-item" key={`${l.product.id}:${variantKey}`}><span className="checkout-item-emoji">{productEmoji(l.product.category)}</span><span><b>{l.product.name}{l.variant ? ` · ${l.variant.label}` : ''}</b><small>{money(unit)} × {l.quantity}</small></span><div className="quantity-control"><button onClick={() => setQuantity(l.product.id, l.variant?.id, l.quantity - 1)} aria-label="Decrease quantity">−</button><b>{l.quantity}</b><button onClick={() => setQuantity(l.product.id, l.variant?.id, l.quantity + 1)} aria-label="Increase quantity">＋</button></div></div> })}</div><div className="summary-lines"><div><span>Subtotal</span><b>{money(subtotal)}</b></div><div><span>Delivery</span><b>{money(store.deliveryFee)}</b></div>{effectivePaymentMethod === 'ADVANCE_COD' && <div><span>Advance before dispatch ({advancePercent}%)</span><b>{money(advanceDue)}</b></div>}<div className="summary-total"><span>Total</span><b>{money(subtotal + (store.deliveryFee ?? 0))}</b></div></div><p className="summary-disclaimer">Wholesale rates apply when a product's minimum quantity is met. Final delivery timing will be confirmed by the store.</p></aside></div></> : <EmptyState title="Your bag is empty" text="Add products from this store before checkout." action={<Button onClick={() => setShowCheckout(false)}>Back to products</Button>}/>}</section></div>}
    {receipt && <div className="modal-backdrop receipt-backdrop"><section className="receipt-modal"><div className="receipt-check">✓</div><Pill tone="green">ORDER RECEIVED</Pill><h2>Thank you, {receipt.customerName.split(' ')[0]}.</h2><p>Your order has been placed. The store can now review your details and confirm fulfilment.</p><div className="receipt-number"><span>Order reference</span><b>{receipt.orderNumber}</b><span>{dateLabel(receipt.createdAt)}</span></div><div className="receipt-total"><span>Total order value</span><b>{money(receipt.totalAmount)}</b></div>{receipt.paymentStatus === 'PAYMENT_PENDING' && <div className="alert alert-warning">Your order is awaiting payment confirmation.</div>}{receipt.paymentStatus === 'PARTIALLY_PAID' && <div className="alert alert-warning">Your advance payment is confirmed. The remaining balance is due on delivery.</div>}<Button className="button-wide" onClick={() => { setReceipt(null); setShowCheckout(false); setCart([]); if (store) sessionStorage.removeItem(`byawasaya-checkout:${store.slug}`); setCheckoutKey(null) }}>Continue shopping</Button></section></div>}
  </div>
}

function CustomerAuthPage({ mode }: { mode: 'login' | 'register' | 'verify' }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const verifyToken = new URLSearchParams(window.location.search).get('token')
  useEffect(() => {
    if (mode !== 'verify') return
    if (!verifyToken) return
    let active = true
    api<{ message: string }>('/api/customer/auth/verify-email', null, { method: 'POST', body: JSON.stringify({ token: verifyToken }) })
      .then(result => { if (active) setMessage(result.message) })
      .catch(e => { if (active) setError(e instanceof Error ? e.message : 'Email verification failed.') })
    return () => { active = false }
  }, [mode, verifyToken])
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError(''); setMessage('')
    try {
      if (mode === 'register') {
        const result = await api<{ message: string }>('/api/customer/auth/register', null, { method: 'POST', body: JSON.stringify({ email, password, fullName, phone }) })
        setMessage(result.message)
      } else {
        await api('/api/customer/auth/login', null, { method: 'POST', body: JSON.stringify({ email, password }) })
        navigate('/customer/orders')
      }
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not complete your request.') }
    finally { setBusy(false) }
  }
  async function resendVerification() {
    setBusy(true); setError(''); setMessage('')
    try {
      const result = await api<{ message: string }>('/api/customer/auth/resend-verification', null,
        { method: 'POST', body: JSON.stringify({ email }) })
      setMessage(result.message)
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not resend the verification email.') }
    finally { setBusy(false) }
  }
  const title = mode === 'register' ? 'Create a customer account' : mode === 'verify' ? 'Verify your email' : 'Sign in to your account'
  const subtitle = mode === 'register' ? 'Use one account to see your order history across participating stores.' : mode === 'verify' ? 'Confirm your email address to activate order history.' : 'Sign in with your verified email and password.'
  const visibleError = error || (mode === 'verify' && !verifyToken ? 'This verification link is incomplete.' : '')
  return <AuthLayout title={title} subtitle={subtitle} sideTitle="Your orders, together." sideText="Keep track of orders you place with stores on Byawasaya.">
    {mode !== 'verify' ? <form className="auth-form" onSubmit={submit}>
      {mode === 'register' && <label>Full name<input value={fullName} onChange={e => setFullName(e.target.value)} required maxLength={120} autoComplete="name"/></label>}
      <label>Email address<input type="email" value={email} onChange={e => setEmail(e.target.value)} required maxLength={180} autoComplete="email"/></label>
      {mode === 'register' && <label>Phone number <small>(optional)</small><input type="tel" value={phone} onChange={e => setPhone(e.target.value)} maxLength={30} autoComplete="tel"/></label>}
      <label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} required minLength={mode === 'register' ? 12 : 1} maxLength={72} autoComplete={mode === 'register' ? 'new-password' : 'current-password'}/>{mode === 'register' && <small>Use at least 12 characters.</small>}</label>
      {visibleError && <div className="alert alert-error">{visibleError}</div>}{message && <div className="alert alert-success">{message}</div>}
      {mode === 'register' && message && <Button type="button" variant="secondary" className="button-wide" disabled={busy} onClick={() => void resendVerification()}>Resend verification email</Button>}
      <Button type="submit" className="button-wide" disabled={busy}>{busy ? 'Please wait…' : mode === 'register' ? 'Create account' : 'Sign in'} <Icon>↗</Icon></Button>
      {mode === 'login' && <p className="auth-switch"><button type="button" onClick={() => navigate('/customer/forgot-password')}>Forgot password?</button></p>}
      <p className="auth-switch">{mode === 'register' ? 'Already have an account?' : 'New to Byawasaya?'} <button type="button" onClick={() => navigate(mode === 'register' ? '/customer/login' : '/customer/register')}>{mode === 'register' ? 'Sign in' : 'Create an account'}</button></p>
    </form> : <div className="auth-form">{Boolean(verifyToken) && !message && !visibleError && <p>Checking the verification link…</p>}{visibleError && <div className="alert alert-error">{visibleError}</div>}{message && <div className="alert alert-success">{message}</div>}<Button className="button-wide" onClick={() => navigate('/customer/login')}>Go to customer sign in <Icon>↗</Icon></Button></div>}
  </AuthLayout>
}

function CustomerOrderHistoryPage() {
  const [orders, setOrders] = useState<CustomerAuthDtosOrder[]>([])
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    Promise.all([api<{ user: { email: string } }>('/api/customer/auth/session'), api<CustomerAuthDtosOrder[]>('/api/customer/orders')])
      .then(([session, history]) => { if (active) { setEmail(session.user.email); setOrders(history) } })
      .catch(e => { if (active) setError(e instanceof Error ? e.message : 'Sign in to see your order history.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])
  async function logout() { try { await api('/api/customer/auth/logout', null, { method: 'POST' }) } catch { /* The session will expire if the API is unavailable. */ } navigate('/customer/login') }
  if (loading) return <div className="session-checking" role="status">Loading your order history…</div>
  if (error) return <AuthLayout title="Your customer account" subtitle="Sign in to see orders linked to your verified email." sideTitle="Your orders, together." sideText="Order history is private to your customer account."><div className="auth-form"><div className="alert alert-error">{error}</div><Button className="button-wide" onClick={() => navigate('/customer/login')}>Customer sign in</Button><button className="back-link" onClick={() => navigate('/customer/register')}>Create a customer account</button></div></AuthLayout>
  return <main className="customer-orders-page"><header className="customer-orders-header"><Logo/><div><span>{email}</span><Button variant="secondary" onClick={() => void logout()}>Sign out</Button></div></header><section className="customer-orders-content"><Pill tone="green">CUSTOMER ACCOUNT</Pill><h1>Your orders</h1><p>Order history across stores where you signed in at checkout.</p>{orders.length ? <div className="customer-order-list">{orders.map(order => <article className="surface-card customer-order-card" key={`${order.businessId}-${order.id}`}><div><small>{order.storeName}</small><h2>{order.orderNumber}</h2><span>{dateLabel(order.createdAt)}</span></div><div><Pill tone={order.orderStatus === 'CANCELLED' ? 'red' : order.orderStatus === 'DELIVERED' ? 'green' : 'blue'}>{pretty(order.orderStatus)}</Pill><Pill tone={order.paymentStatus === 'PAID' ? 'green' : order.paymentStatus === 'PAYMENT_PENDING' ? 'amber' : 'neutral'}>{pretty(order.paymentStatus)}</Pill><b>{money(order.totalAmount)}</b></div><button className="subtle-link" onClick={() => navigate(`/store/${encodeURIComponent(order.storeSlug)}`)}>Visit store ↗</button></article>)}</div> : <EmptyState title="No linked orders yet" text="Sign in to your customer account before checking out, and your order will appear here." action={<Button onClick={() => navigate('/')}>Browse Byawasaya</Button>}/>}</section></main>
}

type CustomerAuthDtosOrder = { id: string; businessId: string; storeName: string; storeSlug: string; orderNumber: string; totalAmount: number; paymentStatus: string; orderStatus: string; createdAt: string }

function CustomerForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setMessage('')
    try {
      const result = await api<{ message: string }>('/api/customer/auth/password/forgot', null,
        { method: 'POST', body: JSON.stringify({ email }) })
      setMessage(result.message)
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not submit the request.') }
    finally { setBusy(false) }
  }
  return <AuthLayout title="Reset your password" subtitle="Enter the email on your customer account. If it is verified, we will send reset instructions." sideTitle="Get back to your orders." sideText="Password reset links expire after 30 minutes and can only be used once.">
    <form className="auth-form" onSubmit={submit}><label>Email address<input type="email" value={email} onChange={e => setEmail(e.target.value)} required maxLength={180} autoComplete="email"/></label>{error && <div className="alert alert-error">{error}</div>}{message && <div className="alert alert-success">{message}</div>}<Button type="submit" className="button-wide" disabled={busy}>{busy ? 'Please wait…' : 'Send reset instructions'} <Icon>↗</Icon></Button><p className="auth-switch"><button type="button" onClick={() => navigate('/customer/login')}>Back to customer sign in</button></p></form>
  </AuthLayout>
}

function CustomerResetPasswordPage() {
  const [token] = useState(() => new URLSearchParams(window.location.search).get('token') ?? '')
  const [password, setPassword] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState(token ? '' : 'This password-reset link is incomplete.')
  const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setMessage('')
    try {
      const result = await api<{ message: string }>('/api/customer/auth/password/reset', null,
        { method: 'POST', body: JSON.stringify({ token, password }) })
      setMessage(result.message)
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not reset your password.') }
    finally { setBusy(false) }
  }
  return <AuthLayout title="Choose a new password" subtitle="Use a new password with at least 12 characters." sideTitle="One secure step." sideText="Reset links are single-use and expire after 30 minutes.">
    <form className="auth-form" onSubmit={submit}><label>New password<input type="password" value={password} onChange={e => setPassword(e.target.value)} required minLength={12} maxLength={72} autoComplete="new-password"/></label>{error && <div className="alert alert-error">{error}</div>}{message && <div className="alert alert-success">{message}</div>}{message ? <Button type="button" className="button-wide" onClick={() => navigate('/customer/login')}>Go to customer sign in</Button> : <Button type="submit" className="button-wide" disabled={busy || !token}>{busy ? 'Updating…' : 'Update password'} <Icon>↗</Icon></Button>}</form>
  </AuthLayout>
}

function PaymentReturnPage() {
  const params = new URLSearchParams(window.location.search)
  const attemptId = params.get('attemptId')
  const slug = params.get('slug')
  const [attempt, setAttempt] = useState<PaymentAttempt | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [check, setCheck] = useState(0)
  useEffect(() => {
    let active = true
    let timer: ReturnType<typeof setTimeout> | undefined
    async function refresh() {
      if (!attemptId || !slug) { setError('The payment return link is incomplete.'); setLoading(false); return }
      try {
        const result = await api<PaymentAttempt>(`/api/store/${encodeURIComponent(slug)}/payments/${encodeURIComponent(attemptId)}`)
        if (!active) return
        setAttempt(result); setError(''); setLoading(false)
        if (['INITIATED', 'PENDING'].includes(result.status) && check < 5) timer = setTimeout(() => setCheck(n => n + 1), 4000)
      } catch (e) { if (active) { setError(e instanceof Error ? e.message : 'Could not confirm payment status.'); setLoading(false) } }
    }
    void refresh()
    return () => { active = false; if (timer) clearTimeout(timer) }
  }, [attemptId, slug, check])
  const paid = attempt?.status === 'COMPLETED'
  return <div className="auth-page"><main className="auth-main payment-return"><div className="auth-main-inner"><Logo/><div className="auth-heading"><h1>{loading ? 'Checking payment…' : paid ? 'Payment received' : attempt?.status === 'FAILED' ? 'Payment not completed' : 'Payment status pending'}</h1><p>{loading ? 'We are confirming your transaction with the provider.' : paid ? 'The provider confirmed your payment. Your store has received the order update.' : attempt?.status === 'FAILED' ? 'No payment was confirmed. You can return to the store and retry.' : 'The provider has not confirmed payment yet. Please wait a moment or check again.'}</p></div>{error && <div className="alert alert-error">{error}</div>}{attempt && <section className="surface-card receipt-total"><span>Order reference · {attempt.orderNumber}</span><b>{pretty(attempt.provider)} · {money(attempt.amountPaisa / 100)}</b><Pill tone={paid ? 'green' : attempt.status === 'FAILED' ? 'red' : 'amber'}>{pretty(attempt.status)}</Pill></section>}<div className="auth-form-actions">{attempt && ['INITIATED', 'PENDING'].includes(attempt.status) && check >= 5 && <Button variant="secondary" onClick={() => { setLoading(true); setCheck(n => n + 1) }}>Check payment again</Button>}{slug && <Button onClick={() => navigate(`/store/${encodeURIComponent(slug)}`)}>Return to store</Button>}</div></div></main></div>
}

function App() {
  const [path, setPath] = useState(window.location.pathname)
  const [token, setToken] = useState<string | null>(null)
  const [user, setUser] = useState<AuthUser | null>(null)
  const [sessionChecked, setSessionChecked] = useState(false)
  useEffect(() => { const handler = () => setPath(window.location.pathname); window.addEventListener('popstate', handler); return () => window.removeEventListener('popstate', handler) }, [])
  useEffect(() => {
    let cancelled = false
    api<SessionPayload>('/api/auth/session').then(data => {
      if (!cancelled) { setToken('cookie'); setUser(data.user) }
    }).catch(() => {
      if (!cancelled) { setToken(null); setUser(null) }
    }).finally(() => { if (!cancelled) setSessionChecked(true) })
    return () => { cancelled = true }
  }, [])
  function login(_cookieMarker: string, nextUser: AuthUser) {
    setToken('cookie'); setUser(nextUser)
    navigate(nextUser.role === 'CUSTOMER' ? '/customer/orders' : isPlatformRole(nextUser.role) ? '/admin/dashboard' : nextUser.storeStatus === 'ACTIVE' ? '/tenant/dashboard' : '/tenant/onboarding')
  }
  async function logout() {
    const destination = user && isPlatformRole(user.role) ? '/admin/login' : '/tenant/login'
    try { await api<void>('/api/auth/logout', null, { method: 'POST' }) } catch { /* Always clear local UI state; the server session expires if it could not be reached. */ }
    csrfToken = null
    setToken(null); setUser(null); navigate(destination)
  }
  if (path === '/customer/login') return <CustomerAuthPage mode="login"/>
  if (path === '/customer/register') return <CustomerAuthPage mode="register"/>
  if (path === '/customer/verify') return <CustomerAuthPage mode="verify"/>
  if (path === '/customer/orders') return <CustomerOrderHistoryPage/>
  if (path === '/customer/forgot-password') return <CustomerForgotPasswordPage/>
  if (path === '/customer/reset-password') return <CustomerResetPasswordPage/>
  if (path === '/' && isTenantSubdomainHost()) return <Storefront key={window.location.hostname} slug={null}/>
  if (path.startsWith('/store/')) return <Storefront key={path} slug={decodeURIComponent(path.slice('/store/'.length).split('/')[0])}/>
  if (path === '/payment/return') return <PaymentReturnPage/>
  if (!sessionChecked) return <div className="session-checking" role="status">Checking your Byawasaya session…</div>
  if (path === '/verify-email') return <VerifyEmailPage/>
  if (path === '/forgot-password') return <ForgotPasswordPage/>
  if (path === '/reset-password') return <ResetPasswordPage/>
  if (path === '/admin/login') return token && user ? (isPlatformRole(user.role) ? <AdminDashboard token={token} user={user} onLogout={logout}/> : <MerchantDashboard token={token} user={user} onLogout={logout}/>) : <Login accountType="PLATFORM" onLogin={login}/>
  if (path === '/tenant/login' || path === '/login') return token && user ? (isPlatformRole(user.role) ? <AdminDashboard token={token} user={user} onLogout={logout}/> : <MerchantDashboard token={token} user={user} onLogout={logout}/>) : <Login accountType="TENANT" onLogin={login}/>
  if (path === '/register') {
    if (!token || !user) return <Register/>
    if (isPlatformRole(user.role)) return <AdminDashboard token={token} user={user} onLogout={logout}/>
    return <OnboardingWorkspace user={user} onLogout={logout}/>
  }
  if (path === '/tenant/billing') {
    if (!token || !user || user.role !== 'STORE_OWNER') return <Login accountType="TENANT" onLogin={login}/>
    return <TenantBillingDashboard token={token} user={user} onLogout={logout}/>
  }
  if (path === '/tenant/onboarding') { if (!token || !user || user.role !== 'STORE_OWNER') return <Login accountType="TENANT" onLogin={login}/>; return <OnboardingWorkspace user={user} onLogout={logout}/> }
  if (path === '/dashboard' || path === '/merchant' || path === '/tenant/dashboard') {
    if (!token || !user || !['STORE_OWNER','STORE_STAFF'].includes(user.role)) return <Login accountType="TENANT" onLogin={login}/>
    return user.storeStatus === 'ACTIVE' ? <MerchantDashboard token={token} user={user} onLogout={logout}/> : user.role === 'STORE_OWNER' ? <OnboardingWorkspace user={user} onLogout={logout}/> : <Login accountType="TENANT" onLogin={login}/>
  }
  if (path === '/admin' || path === '/admin/dashboard') {
    if (!token || !user || !isPlatformRole(user.role)) return <Login accountType="PLATFORM" onLogin={login}/>
    return <AdminDashboard token={token} user={user} onLogout={logout}/>
  }
  if (path === '/admin/security') {
    if (!token || !user || !isPlatformRole(user.role)) return <Login accountType="PLATFORM" onLogin={login}/>
    return <AdminDashboard token={token} user={user} onLogout={logout}/>
  }
  return <Landing/>
}

export default App
