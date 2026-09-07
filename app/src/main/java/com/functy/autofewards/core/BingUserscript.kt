package com.functy.autofewards.core

/**
 * 油猴（Userscript）兼容引擎（Flutter 版 userscript_engine.dart 翻译）。
 *
 * 原生 WebView 没有 GM_* API，做三层适配：
 * 1. [stripMeta]        去掉 ==UserScript== 元数据块
 * 2. [wrapForInjection] 把脚本体包一层 polyfill（GM_addStyle、GM_setValue/
 *    GM_getValue 用 localStorage 模拟、unsafeWindow=window 等）
 * 3. [autoStartPatch]   针对「Microsoft Bing Rewards 自动搜索助手」(greasyfork
 *    538825) 的改造：自动点「开始自动搜索」按钮，带 30min localStorage 去重
 *
 * ⚠️ 血泪坑（Flutter 版 A.12.2）：所有 Kotlin 参数必须插值成 JS 字面量——
 * 裸标识符在 JS 里是未定义变量 → ReferenceError 静默死亡。
 */
object BingUserscript {

    private val META_REGEX =
        Regex("//\\s*==UserScript==([\\s\\S]*?)//\\s*==/UserScript==")

    /** 去掉元数据块（注入时不必要，还能减少解析成本）。 */
    fun stripMeta(source: String): String =
        META_REGEX.replaceFirst(source, "").trim()

    /** 包装脚本：GM polyfill + 错误捕获。 */
    fun wrapForInjection(source: String, storageKey: String = "__gm_storage"): String {
        val body = stripMeta(source)
        return """
(function(){
  'use strict';
  // ---- GM polyfill（最小集，用 localStorage 模拟持久化）----
  var __GM_KEY = ${jsString(storageKey)};
  function __gmStore(){ try{ return JSON.parse(localStorage.getItem(__GM_KEY)||'{}'); }catch(e){ return {}; } }
  function __gmSave(o){ try{ localStorage.setItem(__GM_KEY, JSON.stringify(o)); }catch(e){} }
  window.GM_getValue = function(k, d){ var s=__gmStore(); return (k in s) ? s[k] : d; };
  window.GM_setValue = function(k, v){ var s=__gmStore(); s[k]=v; __gmSave(s); };
  window.GM_deleteValue = function(k){ var s=__gmStore(); delete s[k]; __gmSave(s); };
  window.GM_addStyle = function(css){ var s=document.createElement('style'); s.textContent=css; (document.head||document.documentElement).appendChild(s); };
  window.GM_xmlhttpRequest = function(opt){ // 最小实现，走页面同源 fetch
    fetch(opt.url, {method: opt.method||'GET', headers: opt.headers||{}, body: opt.data})
      .then(function(r){ return r.text().then(function(t){ opt.onload && opt.onload({status:r.status, responseText:t}); }); })
      .catch(function(e){ opt.onerror && opt.onerror(e); });
  };
  if (typeof window.unsafeWindow === 'undefined') { window.unsafeWindow = window; }
  // ---- 用户脚本体 ----
  try {
    $body
  } catch (e) {
    console.error('[UserscriptEngine]', e);
  }
})();
""".trimIndent()
    }

    /**
     * App 侧注入的「启动器」脚本：
     * - 原脚本在 window load 后创建 UI 面板，等待用户点「开始自动搜索」按钮
     * - 我们模拟一次对该按钮的点击（按钮处理逻辑属于脚本自身，不存在 isTrusted 过滤）
     * - localStorage 去重标记 `__gm_autostart_done_at`：点击「开始自动搜索」本身会
     *   触发搜索导航——必须去重，否则每个搜索结果页都重新点击一次形成无限循环。
     *   30 分钟 TTL 与脚本单次刷分会话时长同量级。
     * - 标记必须在点击成功派发之后再写：写早了万一派发失败，30 分钟内就不会再自动启动
     * - [force] = 手动点击顶栏按钮触发，跳过 30 分钟去重标记
     */
    fun autoStartPatch(force: Boolean = false): String = """
(function(){
  console.log('[AutoStart] patch entered');
  var MARK = '__gm_autostart_done_at';
  var last = 0;
  try { last = parseInt(localStorage.getItem(MARK) || '0', 10) || 0; } catch (e) {}
  if (!$force && Date.now() - last < 30 * 60 * 1000) {
    console.log('[AutoStart] 30 分钟内已自动启动过，跳过');
    return;
  }
  var attempts = 0;
  var timer = setInterval(function(){
    attempts++;
    if (attempts > 120) {
      clearInterval(timer);
      console.log('[AutoStart] 60 秒内未找到「开始自动搜索」按钮，放弃');
      return;
    }
    if (attempts % 10 === 0) console.log('[AutoStart] waiting btn x' + attempts);
    // 找「开始自动搜索」按钮（脚本 UI 内 id 随机，用文本匹配）
    var btns = document.querySelectorAll('div[style], button, span');
    var target = null;
    for (var i = 0; i < btns.length; i++) {
      var t = (btns[i].textContent || '').trim();
      if (t === '开始自动搜索') { target = btns[i]; break; }
    }
    if (target) {
      clearInterval(timer);
      var ev = new MouseEvent('click', {bubbles: true, cancelable: true});
      target.dispatchEvent(ev);
      // 标记必须在点击成功派发之后再写
      try { localStorage.setItem(MARK, String(Date.now())); } catch (e) {}
      console.log('[AutoStart] clicked 开始自动搜索');
    }
  }, 500);
})();
""".trimIndent()

    /** JS 字符串字面量（参数必须插值成字面量的工具）。 */
    fun jsString(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    /** 兜底搜索一步：填词提交。返回的 JS 执行后返回 true/false。 */
    fun fallbackSearchStep(term: String): String = """
(function(){
  var box = document.querySelector('#sb_form_q');
  var form = document.querySelector('#sb_form');
  if (!box || !form) return false;
  box.value = ${jsString(term)};
  form.submit();
  return true;
})()
""".trimIndent()

    /** 脚本 UI 是否出现的探测 JS。 */
    val hasScriptUiJs: String = """
!!document.querySelector('#sb_form_q') && (function(){
  var els = document.querySelectorAll('div,button,span');
  for (var i = 0; i < els.length; i++) {
    if ((els[i].textContent || '').trim() === '开始自动搜索') return true;
  }
  return false;
})()
""".trimIndent()
}
