import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import * as jose from "https://deno.land/x/jose@v4.14.4/index.ts"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const { latitude, longitude } = await req.json()
    const locationParam = `${longitude.toFixed(2)},${latitude.toFixed(2)}`

    // 从环境变量获取配置
    const projectId = Deno.env.get('QWEATHER_PROJECT_ID')
    const kid = Deno.env.get('QWEATHER_KID')
    const privateKeyB64 = Deno.env.get('QWEATHER_PRIVATE_KEY_B64')
    const apiHost = Deno.env.get('QWEATHER_API_HOST') || 'devapi.qweather.com'

    if (!projectId || !kid || !privateKeyB64) {
      throw new Error('Missing QWeather configuration in environment variables')
    }

    // 生成 JWT 签名
    const iat = Math.floor(Date.now() / 1000)
    const exp = iat + 900 // 15 分钟有效期

    const privateKeyRaw = Uint8Array.from(atob(privateKeyB64), c => c.charCodeAt(0))
    const key = await jose.importPKCS8(new TextDecoder().decode(privateKeyRaw), 'EdDSA')

    const jwt = await new jose.SignJWT({
      sub: projectId,
      iat,
      exp
    })
      .setProtectedHeader({ alg: 'EdDSA', kid })
      .sign(key)

    const authHeader = `Bearer ${jwt}`

    // 请求和风天气 API
    const baseUrl = `https://${apiHost}/v7/weather`
    
    console.log(`Fetching weather from: ${baseUrl}/now`)
    
    // 1. 实况天气
    const nowRes = await fetch(`${baseUrl}/now?location=${locationParam}&lang=zh`, {
      headers: { 'Authorization': authHeader }
    })
    const nowData = await nowRes.json()
    console.log(`QWeather Now Response [${nowRes.status}]:`, JSON.stringify(nowData))

    // 2. 3日预报
    const dailyRes = await fetch(`${baseUrl}/3d?location=${locationParam}&lang=zh`, {
      headers: { 'Authorization': authHeader }
    })
    const dailyData = await dailyRes.json()
    console.log(`QWeather Daily Response [${dailyRes.status}]:`, JSON.stringify(dailyData))

    if (nowData.code !== '200' || dailyData.code !== '200') {
      return new Response(
        JSON.stringify({
          error: "QWeather API Error",
          now: nowData,
          daily: dailyData
        }),
        { 
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          status: 200 // 保持 200 让 App 能解析到错误码
        }
      )
    }

    return new Response(
      JSON.stringify({
        now: nowData,
        daily: dailyData
      }),
      { 
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200 
      }
    )

  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      { 
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 400 
      }
    )
  }
})
