import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

serve(async (req: Request) => {
  try {
    const authHeader = req.headers.get('Authorization')
    const supabaseAdmin = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    // 1. 验证调用者（Talk / Sync App）的身份
    const { data: { user }, error: userErr } = await supabaseAdmin.auth.getUser(
      authHeader?.replace('Bearer ', '')
    )
    if (userErr || !user) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 })
    }

    // 2. 获取目标 sessionId
    const body = await req.json()
    const sessionId: string = body?.sessionId
    if (!sessionId) {
      return new Response(JSON.stringify({ error: 'Missing sessionId' }), { status: 400 })
    }

    // 3. 校验会话是否存在、是否仍可授权（未过期、未完成）
    const { data: sessionRow, error: sessionErr } = await supabaseAdmin
      .from('qr_login_sessions')
      .select('status, expires_at')
      .eq('session_id', sessionId)
      .maybeSingle()

    if (sessionErr) {
      return new Response(JSON.stringify({ error: `Session query failed: ${sessionErr.message}` }), { status: 500 })
    }
    if (!sessionRow) {
      return new Response(JSON.stringify({ error: 'Session not found' }), { status: 404 })
    }

    if (sessionRow.status !== 'pending') {
      return new Response(JSON.stringify({ error: 'Session is no longer pending' }), { status: 409 })
    }

    const expiresAt = sessionRow.expires_at ? Date.parse(sessionRow.expires_at) : null
    if (expiresAt !== null && !Number.isNaN(expiresAt) && expiresAt <= Date.now()) {
      await supabaseAdmin
        .from('qr_login_sessions')
        .update({ status: 'expired' })
        .eq('session_id', sessionId)
      return new Response(JSON.stringify({ error: 'Session expired' }), { status: 410 })
    }

    // 4. 为该用户生成一次性登录 Token
    const { data: linkData, error: linkErr } = await supabaseAdmin.auth.admin.generateLink({
      type: 'magiclink',
      email: user.email!,
    })
    if (linkErr || !linkData?.properties) {
      return new Response(JSON.stringify({ error: `generateLink failed: ${linkErr?.message}` }), { status: 500 })
    }

    const props = linkData.properties
    const linkUrl = new URL(props.action_link)
    // 优先 6 位数字 OTP，其次 token_hash，最后 token
    const token = props.email_otp
      || linkUrl.searchParams.get('token_hash')
      || linkUrl.searchParams.get('token')

    if (!token) {
      return new Response(JSON.stringify({ error: 'Could not extract token from magic link' }), { status: 500 })
    }

    // 5. 更新数据库，通知桌面端已授权
    const { error: updateErr } = await supabaseAdmin
      .from('qr_login_sessions')
      .update({ status: 'approved', email: user.email, login_token: token })
      .eq('session_id', sessionId)

    if (updateErr) {
      return new Response(JSON.stringify({ error: `DB update failed: ${updateErr.message}` }), { status: 500 })
    }

    return new Response(JSON.stringify({ success: true }), {
      headers: { 'Content-Type': 'application/json' },
    })
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), { status: 500 })
  }
})
