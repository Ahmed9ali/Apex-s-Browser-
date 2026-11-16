package com.example.automation

import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase 5 deterministic JavaScript execution, 6-step pre-action verification,
 * Shadow DOM / same-origin iframe traversal, post-action outcome verification,
 * and wait condition probe builders.
 *
 * Strictly zero external AI calls and zero blind [0] element fallbacks.
 */
object UniversalActionScripts {

    private fun buildCandidateSelectorsJson(step: AutomationStep): String {
        val arr = JSONArray()
        val seen = LinkedHashSet<String>()
        if (step.selector.isNotBlank() && seen.add(step.selector.trim())) {
            arr.put(step.selector.trim())
        }
        step.locatorCandidates
            .sortedWith(compareByDescending<LocatorCandidate> { it.isUnique }.thenByDescending { it.confidence })
            .forEach { cand ->
                val clean = cand.selector.trim()
                if (clean.isNotEmpty() && seen.add(clean)) {
                    arr.put(clean)
                }
            }
        return arr.toString()
    }

    fun buildTargetHelpersJs(
        labelHint: String,
        framePath: List<String> = emptyList(),
        shadowHostPath: List<String> = emptyList()
    ): String {
        val qLabel = JSONObject.quote(labelHint.trim())
        val qFrameChain = JSONArray(framePath).toString()
        val qShadowChain = JSONArray(shadowHostPath).toString()
        return """
            function apexNorm(s) {
                return (s || '').replace(/\s+/g, ' ').trim().toLowerCase();
            }
            function apexNormCompact(s) {
                return apexNorm(s).replace(/[\s_-]+/g, '');
            }
            function apexEscapeCss(s) {
                return String(s || '').replace(/([!"#$%&'()*+,.\/:;<=>?@\[\\\]^`{|}~])/g, '\\$1');
            }
            function apexEscapeAttr(s) {
                return String(s || '').replace(/\\/g, '\\\\').replace(/"/g, '\\"');
            }
            function apexIsStableId(id) {
                if (!id) return false;
                const clean = String(id).trim();
                if (!clean || clean.length > 80 || /\s/.test(clean)) return false;
                if (/^(?:react-select-\d+.*|:r[0-9a-z]+:|mui-\d+|ember\d+|el-id-\d+.*|headlessui-.*-\d+|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|_[0-9a-z]{7,}|\d{5,})$/i.test(clean)) return false;
                if (/^(?:css-[0-9a-z]{5,}|sc-[a-zA-Z0-9]{5,}|jsx-\d+|svelte-[0-9a-z]{5,}|tw-[0-9a-f]{6,}|style_[0-9a-f]{6,})$/.test(clean)) return false;
                if (/\d{6,}/.test(clean)) return false;
                return true;
            }
            function apexOwnDoc(node) {
                try {
                    if (!node) return document;
                    if (node.nodeType === 9) return node;
                    if (typeof ShadowRoot !== 'undefined' && node instanceof ShadowRoot) {
                        return node.ownerDocument || document;
                    }
                    return node.ownerDocument || document;
                } catch (_) {
                    return document;
                }
            }
            function apexOwnWin(doc) {
                try {
                    return (doc && doc.defaultView) ? doc.defaultView : window;
                } catch (_) {
                    return window;
                }
            }
            function apexIsConnected(el) {
                if (!el) return false;
                if (typeof el.isConnected === 'boolean') return el.isConnected;
                try {
                    const root = el.getRootNode ? el.getRootNode() : apexOwnDoc(el);
                    return Boolean(root && (root === document || root.host && apexIsConnected(root.host) || apexOwnDoc(el).documentElement.contains(el)));
                } catch (_) {
                    return true;
                }
            }
            function apexIsVisible(el) {
                if (!el || el.nodeType !== 1) return false;
                const type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                if (type === 'hidden') return false;
                let cur = el;
                let depth = 0;
                while (cur && cur.nodeType === 1 && depth < 32) {
                    if (cur.hasAttribute && (cur.hasAttribute('hidden') || cur.hasAttribute('inert'))) return false;
                    if (String(cur.getAttribute && cur.getAttribute('aria-hidden') || '').toLowerCase() === 'true') return false;
                    try {
                        const win = apexOwnWin(apexOwnDoc(cur));
                        const style = win.getComputedStyle ? win.getComputedStyle(cur) : null;
                        if (style) {
                            if (style.display === 'none' || style.visibility === 'hidden') return false;
                            if (style.opacity !== '' && parseFloat(style.opacity) === 0) return false;
                        }
                    } catch (_) {}
                    const rootNode = cur.getRootNode ? cur.getRootNode() : null;
                    cur = cur.parentElement || (rootNode && rootNode.host ? rootNode.host : null);
                    depth++;
                }
                try {
                    const rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                    if (rect && rect.width === 0 && rect.height === 0) {
                        const clients = el.getClientRects ? el.getClientRects() : null;
                        if (!clients || clients.length === 0) return false;
                    }
                } catch (_) {}
                return true;
            }
            function apexIsDisabled(el) {
                if (!el) return true;
                if (Boolean(el.disabled)) return true;
                if (String(el.getAttribute && el.getAttribute('aria-disabled') || '').toLowerCase() === 'true') return true;
                if (el.closest && el.closest('[inert], fieldset[disabled]')) return true;
                return false;
            }
            function apexIsReadOnly(el) {
                if (!el) return false;
                if (Boolean(el.readOnly)) return true;
                if (String(el.getAttribute && el.getAttribute('aria-readonly') || '').toLowerCase() === 'true') return true;
                return false;
            }
            function apexIsSensitiveEl(el) {
                if (!el) return false;
                const tag = (el.tagName || '').toLowerCase();
                const type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                if (tag === 'input' && type === 'password') return true;
                const hint = [
                    type,
                    el.getAttribute && el.getAttribute('name') || '',
                    el.id || '',
                    el.getAttribute && el.getAttribute('autocomplete') || '',
                    el.getAttribute && el.getAttribute('aria-label') || ''
                ].join(' ').toLowerCase();
                return /(password|passcode|pin\b|secret|token|otp|one-time-code|cvv|cvc|csc|cc-number|cc-csc|ssn|routing)/i.test(hint);
            }
            function apexDetectSecurityChallenge() {
                try {
                    const challengeEl = document.querySelector('[data-captcha-challenge="true"], .g-recaptcha, .h-captcha, .cf-turnstile, iframe[src*="recaptcha"], iframe[src*="turnstile"], iframe[src*="hcaptcha"]');
                    if (challengeEl && apexIsVisible(challengeEl)) {
                        return true;
                    }
                } catch (_) {}
                return false;
            }
            function apexGetFieldGroupLabel(el) {
                if (!el || !el.tagName) return '';
                const tag = (el.tagName || '').toLowerCase();
                const role = (el.getAttribute && el.getAttribute('role') || '').toLowerCase();
                if (tag === 'button' || tag === 'a' || tag === 'summary' || tag === 'option' || tag === 'label' ||
                    role === 'button' || role === 'link' || role === 'option' || role === 'menuitem') {
                    return '';
                }
                const type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                if (type === 'submit' || type === 'button' || type === 'reset' || type === 'hidden') return '';
                const doc = apexOwnDoc(el);
                function extractCandidate(node, requireExplicit) {
                    if (!node || node.nodeType !== 1 || node === el || (node.contains && node.contains(el))) return '';
                    const nTag = (node.tagName || '').toLowerCase();
                    if (nTag === 'h1' || nTag === 'h2' || nTag === 'h3' || nTag === 'h4' || nTag === 'h5' || nTag === 'h6' ||
                        nTag === 'button' || nTag === 'a' || nTag === 'input' || nTag === 'select' || nTag === 'textarea' || nTag === 'form') {
                        return '';
                    }
                    const forAttr = node.getAttribute && node.getAttribute('for');
                    if (forAttr && (!el.id || forAttr !== el.id) && doc && doc.getElementById && doc.getElementById(forAttr)) return '';
                    if (node.querySelector && node.querySelector('input:not([type="hidden"]), select, textarea, button, a[href]')) return '';
                    const nCls = String(node.className || '').toLowerCase();
                    const isExplicit = nTag === 'label' || nTag === 'legend' || /(?:^|[\s_-])(label|field-label|form-label|control-label|input-label)(?:$|[\s_-])/.test(nCls);
                    if (requireExplicit && !isExplicit && ((nTag !== 'span' && nTag !== 'div') || nCls.indexOf('label') < 0)) return '';
                    const raw = (node.innerText || node.textContent || '').replace(/\s+/g, ' ').trim();
                    if (!raw || raw.length > 48) return '';
                    if (!isExplicit && /[.!?]$/.test(raw)) return '';
                    return raw;
                }
                try {
                    const prev = el.previousElementSibling;
                    if (prev) {
                        const pCtrls = el.parentElement && el.parentElement.querySelectorAll
                            ? el.parentElement.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), select, textarea').length
                            : 2;
                        const pTxt = extractCandidate(prev, pCtrls > 1);
                        if (pTxt) return pTxt;
                    }
                    let cur = el.parentElement;
                    let depth = 0;
                    while (cur && cur.nodeType === 1 && depth < 3) {
                        const cTag = (cur.tagName || '').toLowerCase();
                        if (cTag === 'form' || cTag === 'body' || cTag === 'html' || cTag === 'main' || cTag === 'section' || cTag === 'fieldset') break;
                        const ctrls = cur.querySelectorAll
                            ? cur.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), select, textarea, [role="combobox"], [role="listbox"]')
                            : [];
                        if (ctrls.length <= 1) {
                            const children = cur.children ? Array.from(cur.children) : [];
                            for (let i = 0; i < children.length; i++) {
                                const child = children[i];
                                if (child === el || (child.contains && child.contains(el))) break;
                                const cTxt = extractCandidate(child, false);
                                if (cTxt) return cTxt;
                            }
                            const cPrev = cur.previousElementSibling;
                            if (cPrev) {
                                const parentCtrls = cur.parentElement && cur.parentElement.querySelectorAll
                                    ? cur.parentElement.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), select, textarea').length
                                    : 2;
                                const cpTxt = extractCandidate(cPrev, parentCtrls > 1);
                                if (cpTxt) return cpTxt;
                            }
                        }
                        cur = cur.parentElement;
                        depth++;
                    }
                } catch (_) {}
                return '';
            }
            function apexResolveLabel(el) {
                const doc = apexOwnDoc(el);
                const root = (el.getRootNode && el.getRootNode()) || doc;
                let parts = [];
                try {
                    const aria = el.getAttribute && el.getAttribute('aria-label');
                    if (aria) parts.push(aria);
                    const lb = el.getAttribute && el.getAttribute('aria-labelledby');
                    if (lb) {
                        lb.split(/\s+/).forEach(function(refId) {
                            const ref = (root && root.getElementById ? root.getElementById(refId) : null) ||
                                (doc && doc.getElementById ? doc.getElementById(refId) : null);
                            if (ref) parts.push(ref.innerText || ref.textContent || '');
                        });
                    }
                    const ph = el.getAttribute && el.getAttribute('placeholder');
                    if (ph) parts.push(ph);
                    const nm = el.getAttribute && el.getAttribute('name');
                    if (nm) parts.push(nm);
                    const ti = el.getAttribute && el.getAttribute('title');
                    if (ti) parts.push(ti);
                    if (el.id) {
                        parts.push(el.id);
                        const lbl = (root && root.querySelector ? root.querySelector('label[for="' + el.id.replace(/"/g, '\\"') + '"]') : null) ||
                            (doc && doc.querySelector ? doc.querySelector('label[for="' + el.id.replace(/"/g, '\\"') + '"]') : null);
                        if (lbl) parts.push(lbl.innerText || lbl.textContent || '');
                    }
                    const wrap = el.closest ? el.closest('label') : null;
                    if (wrap) parts.push(wrap.innerText || wrap.textContent || '');
                    const fgLbl = apexGetFieldGroupLabel(el);
                    if (fgLbl) parts.push(fgLbl);
                    const vis = (el.innerText || el.textContent || '').trim();
                    if (vis) parts.push(vis);
                    else if (!apexIsSensitiveEl(el) && el.value) parts.push(el.value);
                } catch (_) {}
                return apexNorm(parts.join(' '));
            }
            function apexDescribeAccessibleName(el) {
                const doc = apexOwnDoc(el);
                const root = (el.getRootNode && el.getRootNode()) || doc;
                try {
                    const lb = el.getAttribute && el.getAttribute('aria-labelledby');
                    if (lb) {
                        const txt = lb.split(/\s+/).map(function(id) {
                            const ref = (root && root.getElementById ? root.getElementById(id) : null) || (doc && doc.getElementById ? doc.getElementById(id) : null);
                            return ref ? (ref.innerText || ref.textContent || '').trim() : '';
                        }).filter(Boolean).join(' ');
                        if (txt) return txt.slice(0, 100);
                    }
                    const aria = el.getAttribute && el.getAttribute('aria-label');
                    if (aria && aria.trim()) return aria.trim().slice(0, 100);
                    if (el.id) {
                        const lbl = (root && root.querySelector ? root.querySelector('label[for="' + apexEscapeAttr(el.id) + '"]') : null) ||
                            (doc && doc.querySelector ? doc.querySelector('label[for="' + apexEscapeAttr(el.id) + '"]') : null);
                        if (lbl && (lbl.innerText || lbl.textContent || '').trim()) {
                            return (lbl.innerText || lbl.textContent).replace(/\s+/g, ' ').trim().slice(0, 100);
                        }
                    }
                    const wrap = el.closest ? el.closest('label') : null;
                    if (wrap && (wrap.innerText || wrap.textContent || '').trim()) {
                        return (wrap.innerText || wrap.textContent).replace(/\s+/g, ' ').trim().slice(0, 100);
                    }
                    const fgLbl = apexGetFieldGroupLabel(el);
                    if (fgLbl) return fgLbl.slice(0, 100);
                    const txt = (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
                    if (txt) return txt.slice(0, 100);
                    const ph = el.getAttribute && el.getAttribute('placeholder');
                    if (ph && ph.trim()) return ph.trim().slice(0, 100);
                    const nm = el.getAttribute && el.getAttribute('name');
                    if (nm && nm.trim()) return nm.trim().slice(0, 100);
                } catch (_) {}
                return '';
            }
            function apexGetParentContext(el) {
                try {
                    const parts = [];
                    let p = el.parentElement;
                    let steps = 0;
                    while (p && p.nodeType === 1 && steps < 5 && parts.length < 2) {
                        const pt = (p.tagName || '').toLowerCase();
                        if (pt === 'form' || pt === 'header' || pt === 'nav' || pt === 'footer' || pt === 'main' || pt === 'aside' || pt === 'section' || pt === 'fieldset' ||
                            p.getAttribute('role') || p.getAttribute('aria-label') || apexIsStableId(p.id)) {
                            let desc = pt;
                            if (apexIsStableId(p.id)) desc += '#' + p.id;
                            else if (p.getAttribute('data-testid')) desc += '[data-testid="' + p.getAttribute('data-testid') + '"]';
                            else if (p.getAttribute('role')) desc += '[role="' + p.getAttribute('role') + '"]';
                            if (p.getAttribute('aria-label')) desc += ' "' + p.getAttribute('aria-label').slice(0, 32) + '"';
                            parts.unshift(desc);
                        }
                        p = p.parentElement;
                        steps++;
                    }
                    return parts.join(' > ').slice(0, 120);
                } catch (_) {
                    return '';
                }
            }
            function apexGetNearbyText(el) {
                try {
                    const fs = el.closest ? el.closest('fieldset') : null;
                    if (fs) {
                        const lg = fs.querySelector('legend');
                        if (lg && (lg.innerText || lg.textContent || '').trim()) {
                            return (lg.innerText || lg.textContent).replace(/\s+/g, ' ').trim().slice(0, 80);
                        }
                    }
                    let sib = el.previousElementSibling;
                    let tries = 0;
                    while (sib && tries < 2) {
                        if (!apexIsSensitiveEl(sib)) {
                            const txt = (sib.innerText || sib.textContent || '').replace(/\s+/g, ' ').trim();
                            if (txt) return txt.slice(0, 80);
                        }
                        sib = sib.previousElementSibling;
                        tries++;
                    }
                    if (el.parentElement && el.parentElement.previousElementSibling && !apexIsSensitiveEl(el.parentElement.previousElementSibling)) {
                        const ptxt = (el.parentElement.previousElementSibling.innerText || el.parentElement.previousElementSibling.textContent || '').replace(/\s+/g, ' ').trim();
                        if (ptxt) return ptxt.slice(0, 80);
                    }
                } catch (_) {}
                return '';
            }
            function apexGetContextsForEl(el) {
                const framePath = [];
                const shadowPath = [];
                try {
                    let rootNode = el.getRootNode ? el.getRootNode() : apexOwnDoc(el);
                    let guard = 0;
                    while (typeof ShadowRoot !== 'undefined' && rootNode instanceof ShadowRoot && rootNode.host && guard < 8) {
                        const h = rootNode.host;
                        const ht = (h.tagName || '').toLowerCase();
                        const hs = apexIsStableId(h.id) ? (ht + '#' + apexEscapeCss(h.id)) :
                            (h.getAttribute('data-testid') ? (ht + '[data-testid="' + apexEscapeAttr(h.getAttribute('data-testid')) + '"]') : ht);
                        shadowPath.unshift(hs);
                        rootNode = h.getRootNode ? h.getRootNode() : apexOwnDoc(h);
                        guard++;
                    }
                    let win = apexOwnWin(apexOwnDoc(el));
                    guard = 0;
                    while (win && win !== window && win.frameElement && guard < 6) {
                        const f = win.frameElement;
                        const fid = f.id || '';
                        const fname = f.getAttribute && f.getAttribute('name') || '';
                        const fsel = apexIsStableId(fid) ? ('iframe#' + apexEscapeCss(fid)) :
                            (fname ? ('iframe[name="' + apexEscapeAttr(fname) + '"]') : 'iframe');
                        framePath.unshift(fsel);
                        win = win.parent;
                        guard++;
                    }
                } catch (_) {}
                return { framePath: framePath, shadowHostPath: shadowPath };
            }
            function apexBuildRecommendedSelector(el) {
                const root = (el.getRootNode && el.getRootNode()) || apexOwnDoc(el);
                const tag = (el.tagName || '').toLowerCase() || '*';
                function isUniqueLocal(sel) {
                    try { return root.querySelectorAll(sel).length === 1; } catch (_) { return false; }
                }
                const cands = [];
                if (apexIsStableId(el.id)) cands.push('#' + apexEscapeCss(el.id));
                ['data-testid', 'data-test', 'data-qa', 'data-cy', 'data-action'].forEach(function(a) {
                    const v = el.getAttribute && el.getAttribute(a);
                    if (v) cands.push(tag + '[' + a + '="' + apexEscapeAttr(v) + '"]');
                });
                const aria = el.getAttribute && el.getAttribute('aria-label');
                const role = el.getAttribute && el.getAttribute('role');
                if (aria) {
                    if (role) cands.push(tag + '[role="' + apexEscapeAttr(role) + '"][aria-label="' + apexEscapeAttr(aria) + '"]');
                    cands.push(tag + '[aria-label="' + apexEscapeAttr(aria) + '"]');
                }
                const nameAttr = el.getAttribute && el.getAttribute('name');
                if (nameAttr) cands.push(tag + '[name="' + apexEscapeAttr(nameAttr) + '"]');
                const hrefAttr = tag === 'a' && el.getAttribute ? el.getAttribute('href') : '';
                if (hrefAttr && hrefAttr !== '#' && hrefAttr.indexOf('javascript:') !== 0) {
                    cands.push('a[href="' + apexEscapeAttr(hrefAttr) + '"]');
                }
                const phAttr = el.getAttribute && el.getAttribute('placeholder');
                if (phAttr) cands.push(tag + '[placeholder="' + apexEscapeAttr(phAttr) + '"]');

                for (let i = 0; i < cands.length; i++) {
                    if (isUniqueLocal(cands[i])) {
                        const ctx = apexGetContextsForEl(el);
                        const prefix = ctx.shadowHostPath.length > 0 ? (ctx.shadowHostPath.join(' >>> ') + ' >>> ') :
                            (ctx.framePath.length > 0 ? (ctx.framePath.join(' >> ') + ' >> ') : '');
                        return prefix + cands[i];
                    }
                }
                // Container-scoped refinement
                let anc = el.parentElement;
                let aSteps = 0;
                while (anc && anc.nodeType === 1 && aSteps < 5) {
                    const at = (anc.tagName || '').toLowerCase();
                    let aSel = '';
                    if (apexIsStableId(anc.id)) aSel = at + '#' + apexEscapeCss(anc.id);
                    else if (anc.getAttribute && anc.getAttribute('data-testid')) aSel = at + '[data-testid="' + apexEscapeAttr(anc.getAttribute('data-testid')) + '"]';
                    else if (at === 'form' || at === 'header' || at === 'nav' || at === 'footer' || at === 'main' || at === 'aside') aSel = at;
                    if (aSel) {
                        for (let i = 0; i < cands.length; i++) {
                            const scoped = aSel + ' ' + cands[i];
                            if (isUniqueLocal(scoped)) {
                                const ctx = apexGetContextsForEl(el);
                                const prefix = ctx.shadowHostPath.length > 0 ? (ctx.shadowHostPath.join(' >>> ') + ' >>> ') :
                                    (ctx.framePath.length > 0 ? (ctx.framePath.join(' >> ') + ' >> ') : '');
                                return prefix + scoped;
                            }
                        }
                    }
                    anc = anc.parentElement;
                    aSteps++;
                }
                // Structural CSS path fallback (up to 8 levels) - only break when uniquely matching
                const segs = [];
                let cur = el;
                let lvl = 0;
                while (cur && cur.nodeType === 1 && lvl < 8) {
                    const ct = (cur.tagName || '').toLowerCase();
                    if (!ct || ct === 'html' || ct === 'body') break;
                    let seg = '';
                    let anchored = false;
                    if (apexIsStableId(cur.id)) {
                        const idSeg = (cur === el && segs.length === 0) ? ('#' + apexEscapeCss(cur.id)) : (ct + '#' + apexEscapeCss(cur.id));
                        if (isUniqueLocal([idSeg].concat(segs).join(' > '))) {
                            segs.unshift(idSeg);
                            break;
                        }
                        seg = idSeg;
                        anchored = true;
                    } else {
                        const dtVal = cur.getAttribute && (cur.getAttribute('data-testid') || cur.getAttribute('data-test'));
                        if (dtVal) {
                            const dtAttr = cur.getAttribute('data-testid') ? 'data-testid' : 'data-test';
                            const dtSeg = ct + '[' + dtAttr + '="' + apexEscapeAttr(dtVal) + '"]';
                            if (isUniqueLocal([dtSeg].concat(segs).join(' > '))) {
                                segs.unshift(dtSeg);
                                break;
                            }
                            seg = dtSeg;
                            anchored = true;
                        }
                    }
                    if (!anchored) {
                        let nth = 1;
                        let s = cur.previousElementSibling;
                        while (s) {
                            if ((s.tagName || '').toLowerCase() === ct) nth++;
                            s = s.previousElementSibling;
                        }
                        let sameAfter = false;
                        let ns = cur.nextElementSibling;
                        while (ns) {
                            if ((ns.tagName || '').toLowerCase() === ct) { sameAfter = true; break; }
                            ns = ns.nextElementSibling;
                        }
                        seg = (nth > 1 || sameAfter) ? (ct + ':nth-of-type(' + nth + ')') : ct;
                    }
                    segs.unshift(seg);
                    if (isUniqueLocal(segs.join(' > '))) break;
                    cur = cur.parentElement;
                    lvl++;
                }
                const localFinal = segs.join(' > ') || tag;
                const ctx = apexGetContextsForEl(el);
                const prefix = ctx.shadowHostPath.length > 0 ? (ctx.shadowHostPath.join(' >>> ') + ' >>> ') :
                    (ctx.framePath.length > 0 ? (ctx.framePath.join(' >> ') + ' >> ') : '');
                return prefix + localFinal;
            }
            function apexCollectCandidateInfo(el, index) {
                const tag = (el.tagName || '').toLowerCase();
                const isSensitive = apexIsSensitiveEl(el);
                const role = (el.getAttribute && el.getAttribute('role') || '').toLowerCase() || (tag === 'a' ? 'link' : tag);
                const accessibleName = apexDescribeAccessibleName(el);
                const visibleText = isSensitive ? '' : (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 100);
                const safeAttrs = {};
                const allowed = ['type', 'role', 'name', 'href', 'placeholder', 'aria-label', 'data-testid', 'data-test', 'data-qa', 'data-cy', 'data-action'];
                allowed.forEach(function(k) {
                    if (isSensitive && k === 'placeholder') return;
                    const v = el.getAttribute && el.getAttribute(k);
                    if (v && String(v).trim()) safeAttrs[k] = String(v).trim().slice(0, 120);
                });
                if (apexIsStableId(el.id)) safeAttrs.id = String(el.id).trim();
                const ctx = apexGetContextsForEl(el);
                return {
                    index: index,
                    tagName: tag,
                    role: role,
                    accessibleName: accessibleName,
                    visibleText: visibleText,
                    attributes: safeAttrs,
                    parentContext: apexGetParentContext(el),
                    nearbyText: isSensitive ? '' : apexGetNearbyText(el),
                    framePath: ctx.framePath,
                    shadowHostPath: ctx.shadowHostPath,
                    isVisible: apexIsVisible(el),
                    isEnabled: !apexIsDisabled(el),
                    recommendedSelector: apexBuildRecommendedSelector(el)
                };
            }
            function apexQueryChain(fullSelector) {
                const raw = String(fullSelector || '').trim();
                if (!raw) return { ok: false, code: 'INVALID_SELECTOR', matches: [] };
                try {
                    const hasChain = raw.indexOf('>>') >= 0;
                    if (!hasChain) {
                        return { ok: true, matches: Array.from(document.querySelectorAll(raw)), doc: document };
                    }
                    const tokens = [];
                    let idx = 0;
                    let pendingOp = 'ROOT';
                    while (idx < raw.length) {
                        const nextTriple = raw.indexOf('>>>', idx);
                        const nextDouble = raw.indexOf('>>', idx);
                        let splitAt = -1;
                        let opLen = 0;
                        let nextOp = '';
                        if (nextTriple >= 0 && (nextDouble < 0 || nextTriple <= nextDouble)) {
                            splitAt = nextTriple;
                            opLen = 3;
                            nextOp = 'SHADOW';
                        } else if (nextDouble >= 0) {
                            splitAt = nextDouble;
                            opLen = 2;
                            nextOp = 'IFRAME';
                        }
                        if (splitAt < 0) {
                            tokens.push({ op: pendingOp, sel: raw.slice(idx).trim() });
                            break;
                        } else {
                            tokens.push({ op: pendingOp, sel: raw.slice(idx, splitAt).trim() });
                            pendingOp = nextOp;
                            idx = splitAt + opLen;
                        }
                    }
                    let currentRoots = [document];
                    for (let t = 0; t < tokens.length; t++) {
                        const tok = tokens[t];
                        if (!tok.sel) return { ok: false, code: 'INVALID_SELECTOR', matches: [] };
                        if (t > 0) {
                            const nextRoots = [];
                            for (let r = 0; r < currentRoots.length; r++) {
                                const host = currentRoots[r];
                                if (tok.op === 'SHADOW') {
                                    if (host && host.shadowRoot) {
                                        nextRoots.push(host.shadowRoot);
                                    } else {
                                        return { ok: false, code: 'ELEMENT_NOT_FOUND', matches: [], msg: 'Open shadowRoot unavailable on host.' };
                                    }
                                } else if (tok.op === 'IFRAME') {
                                    try {
                                        const subDoc = host && (host.contentDocument || (host.contentWindow && host.contentWindow.document));
                                        if (!subDoc) {
                                            return { ok: false, code: 'CROSS_ORIGIN_BLOCKED', matches: [], msg: 'Iframe document is cross-origin or unloaded.' };
                                        }
                                        nextRoots.push(subDoc);
                                    } catch (_) {
                                        return { ok: false, code: 'CROSS_ORIGIN_BLOCKED', matches: [], msg: 'Blocked cross-origin iframe access.' };
                                    }
                                }
                            }
                            currentRoots = nextRoots;
                        }
                        const stepMatches = [];
                        for (let r = 0; r < currentRoots.length; r++) {
                            const found = Array.from(currentRoots[r].querySelectorAll(tok.sel));
                            for (let f = 0; f < found.length; f++) stepMatches.push(found[f]);
                        }
                        if (t < tokens.length - 1) {
                            if (stepMatches.length === 0) {
                                return { ok: false, code: 'ELEMENT_NOT_FOUND', matches: [] };
                            }
                            if (stepMatches.length > 1) {
                                return { ok: false, code: 'ELEMENT_AMBIGUOUS', matches: stepMatches, msg: 'Multiple container hosts matched "' + tok.sel + '".' };
                            }
                            currentRoots = stepMatches;
                        } else {
                            return { ok: true, matches: stepMatches };
                        }
                    }
                    return { ok: false, code: 'ELEMENT_NOT_FOUND', matches: [] };
                } catch (e) {
                    return { ok: false, code: 'INVALID_SELECTOR', matches: [], msg: 'Invalid selector "' + raw + '": ' + String(e && e.message ? e.message : e) };
                }
            }
            function apexDeepQueryAll(cssSel) {
                const collected = [];
                function walk(root, depth) {
                    if (!root || depth > 4) return;
                    try {
                        const found = root.querySelectorAll(cssSel);
                        for (let i = 0; i < found.length; i++) collected.push(found[i]);
                        const all = root.querySelectorAll('*');
                        for (let i = 0; i < all.length; i++) {
                            const node = all[i];
                            if (node.shadowRoot) walk(node.shadowRoot, depth + 1);
                            if ((node.tagName || '').toLowerCase() === 'iframe') {
                                try {
                                    const subDoc = node.contentDocument || (node.contentWindow && node.contentWindow.document);
                                    if (subDoc) walk(subDoc, depth + 1);
                                } catch (_) {}
                            }
                        }
                    } catch (_) {}
                }
                walk(document, 0);
                return collected;
            }
            function apexResolveWithCandidates(candidateList, labelHint, defaultQuery, opts) {
                const options = opts || {};
                const requireWritable = Boolean(options.requireWritable);
                const allowOffscreen = Boolean(options.allowOffscreen);
                const ignoreDisabled = Boolean(options.ignoreDisabled);
                const normHint = apexNorm(labelHint || $qLabel);
                const compactHint = apexNormCompact(normHint);
                const frameHint = $qFrameChain;
                const shadowHint = $qShadowChain;
                const hintPrefix = (Array.isArray(shadowHint) && shadowHint.length > 0) ? (shadowHint.join(' >>> ') + ' >>> ') :
                    ((Array.isArray(frameHint) && frameHint.length > 0) ? (frameHint.join(' >> ') + ' >> ') : '');

                const attempts = [];
                let sawAmbiguous = false;
                let ambiguousCount = 0;
                let ambiguousElements = [];
                let blockedCode = '';
                let blockedMsg = '';
                let chosen = null;
                let matchedVia = '';
                let usedFallback = false;

                function classifyStrategy(sel, idx) {
                    if (sel.indexOf('>>>') >= 0) return 'SHADOW_DOM_CHAIN';
                    if (sel.indexOf('>>') >= 0) return 'IFRAME_CHAIN';
                    if (sel.indexOf('#') === 0 || (sel.indexOf('#') > 0 && sel.indexOf(' ') < 0)) return 'STABLE_ID';
                    if (sel.indexOf('[data-test') >= 0 || sel.indexOf('[data-qa') >= 0 || sel.indexOf('[data-cy') >= 0) return 'STABLE_DATA_ATTR';
                    if (sel.indexOf('[aria-label') >= 0) return 'ARIA_ROLE_AND_NAME';
                    if (sel.indexOf('[name=') >= 0) return 'STABLE_NAME';
                    if (sel.indexOf('[href=') >= 0) return 'HREF_ATTR';
                    if (sel.indexOf('[placeholder=') >= 0) return 'PLACEHOLDER_ATTR';
                    return idx === 0 ? 'RECORDED_PRIMARY' : 'SCOPED_CSS';
                }

                const selectorQueue = [];
                const rawQueue = Array.isArray(candidateList) ? candidateList : [];
                for (let i = 0; i < rawQueue.length; i++) {
                    const s = String(rawQueue[i] || '').trim();
                    if (!s) continue;
                    const scoped = (hintPrefix && s.indexOf('>>') < 0) ? (hintPrefix + s) : s;
                    if (selectorQueue.indexOf(scoped) < 0) selectorQueue.push(scoped);
                    if (scoped !== s && selectorQueue.indexOf(s) < 0) selectorQueue.push(s);
                }

                for (let idx = 0; idx < selectorQueue.length; idx++) {
                    const sel = selectorQueue[idx];
                    const qRes = apexQueryChain(sel);
                    if (!qRes.ok) {
                        attempts.push({
                            strategy: classifyStrategy(sel, idx),
                            selector: sel,
                            matchCount: qRes.matches ? qRes.matches.length : 0,
                            visibleCount: 0,
                            interactableCount: 0,
                            selected: false,
                            rejectedReason: qRes.code || 'QUERY_FAILED'
                        });
                        if (qRes.code === 'CROSS_ORIGIN_BLOCKED' || qRes.code === 'ELEMENT_AMBIGUOUS') {
                            blockedCode = qRes.code;
                            blockedMsg = qRes.msg || '';
                            if (qRes.code === 'ELEMENT_AMBIGUOUS' && qRes.matches && qRes.matches.length > ambiguousElements.length) {
                                ambiguousElements = qRes.matches.slice(0, 12);
                                ambiguousCount = qRes.matches.length;
                                sawAmbiguous = true;
                            }
                        } else if (qRes.code === 'INVALID_SELECTOR' && selectorQueue.length === 1 && !normHint) {
                            return {
                                ok: false,
                                code: 'INVALID_SELECTOR',
                                msg: qRes.msg || 'Invalid selector',
                                diagnostics: {
                                    recordedLocator: selectorQueue[0] || '',
                                    targetLabel: labelHint || $qLabel,
                                    totalMatches: 0,
                                    visibleMatches: 0,
                                    interactableMatches: 0,
                                    finalOutcome: 'INVALID_SELECTOR',
                                    attemptedCandidates: attempts
                                }
                            };
                        }
                        continue;
                    }
                    const connected = qRes.matches.filter(function(el) { return apexIsConnected(el); });
                    let candidates = connected.filter(function(el) { return allowOffscreen || apexIsVisible(el); });
                    const interactableCnt = candidates.filter(function(el) { return !apexIsDisabled(el); }).length;

                    if (candidates.length === 0 && qRes.matches.length > 0 && !allowOffscreen) {
                        attempts.push({
                            strategy: classifyStrategy(sel, idx),
                            selector: sel,
                            matchCount: qRes.matches.length,
                            visibleCount: 0,
                            interactableCount: 0,
                            selected: false,
                            rejectedReason: 'NOT_VISIBLE'
                        });
                        blockedCode = 'ELEMENT_NOT_INTERACTABLE';
                        blockedMsg = 'Target "' + sel + '" is hidden or not interactable.';
                        continue;
                    }
                    if (candidates.length > 1 && normHint) {
                        const exact = candidates.filter(function(el) { return apexResolveLabel(el) === normHint; });
                        if (exact.length === 1) {
                            candidates = exact;
                        } else if (exact.length > 1) {
                            candidates = exact;
                        } else {
                            const compactMatched = compactHint ? candidates.filter(function(el) { return apexNormCompact(apexResolveLabel(el)) === compactHint; }) : [];
                            if (compactMatched.length === 1) {
                                candidates = compactMatched;
                            } else if (compactMatched.length > 1) {
                                candidates = compactMatched;
                            } else {
                                const narrowed = candidates.filter(function(el) {
                                    const lbl = apexResolveLabel(el);
                                    return lbl.indexOf(normHint) >= 0 || (compactHint.length >= 4 && apexNormCompact(lbl).indexOf(compactHint) >= 0);
                                });
                                if (narrowed.length > 0) candidates = narrowed;
                            }
                        }
                    }
                    if (candidates.length === 1) {
                        chosen = candidates[0];
                        matchedVia = sel;
                        usedFallback = idx > 0;
                        attempts.push({
                            strategy: classifyStrategy(sel, idx),
                            selector: sel,
                            matchCount: qRes.matches.length,
                            visibleCount: candidates.length,
                            interactableCount: interactableCnt,
                            selected: true,
                            rejectedReason: ''
                        });
                        break;
                    } else if (candidates.length > 1) {
                        sawAmbiguous = true;
                        ambiguousCount = candidates.length;
                        if (ambiguousElements.length === 0 || candidates.length < ambiguousElements.length) {
                            ambiguousElements = candidates.slice(0, 12);
                        }
                        attempts.push({
                            strategy: classifyStrategy(sel, idx),
                            selector: sel,
                            matchCount: qRes.matches.length,
                            visibleCount: candidates.length,
                            interactableCount: interactableCnt,
                            selected: false,
                            rejectedReason: 'AMBIGUOUS_' + candidates.length + '_MATCHES'
                        });
                    } else {
                        attempts.push({
                            strategy: classifyStrategy(sel, idx),
                            selector: sel,
                            matchCount: 0,
                            visibleCount: 0,
                            interactableCount: 0,
                            selected: false,
                            rejectedReason: 'NO_MATCH'
                        });
                    }
                }

                if (!chosen && normHint && defaultQuery) {
                    const scopedQuery = hintPrefix ? (hintPrefix + defaultQuery) : '';
                    let pool = [];
                    if (scopedQuery) {
                        const sRes = apexQueryChain(scopedQuery);
                        if (sRes.ok) pool = sRes.matches;
                    }
                    if (pool.length === 0) {
                        pool = apexDeepQueryAll(defaultQuery);
                    }
                    pool = pool.filter(function(el) {
                        return apexIsConnected(el) && (allowOffscreen || apexIsVisible(el));
                    });
                    const exact = pool.filter(function(el) { return apexResolveLabel(el) === normHint; });
                    const compact = (exact.length === 0 && compactHint) ? pool.filter(function(el) {
                        return apexNormCompact(apexResolveLabel(el)) === compactHint;
                    }) : [];
                    const partial = (exact.length === 0 && compact.length === 0) ? pool.filter(function(el) {
                        const lbl = apexResolveLabel(el);
                        return lbl.indexOf(normHint) >= 0 || (compactHint.length >= 4 && apexNormCompact(lbl).indexOf(compactHint) >= 0);
                    }) : [];
                    const candidates = exact.length > 0 ? exact : (compact.length > 0 ? compact : partial);
                    if (candidates.length === 1) {
                        chosen = candidates[0];
                        matchedVia = 'label:' + normHint;
                        usedFallback = selectorQueue.length > 0;
                        attempts.push({
                            strategy: 'SCOPED_TEXT',
                            selector: 'label:' + normHint,
                            matchCount: candidates.length,
                            visibleCount: candidates.length,
                            interactableCount: candidates.filter(function(el) { return !apexIsDisabled(el); }).length,
                            selected: true,
                            rejectedReason: ''
                        });
                    } else if (candidates.length > 1) {
                        attempts.push({
                            strategy: 'SCOPED_TEXT',
                            selector: 'label:' + normHint,
                            matchCount: candidates.length,
                            visibleCount: candidates.length,
                            interactableCount: candidates.filter(function(el) { return !apexIsDisabled(el); }).length,
                            selected: false,
                            rejectedReason: 'AMBIGUOUS_' + candidates.length + '_MATCHES'
                        });
                        const ambigInfos = candidates.slice(0, 12).map(function(el, i) { return apexCollectCandidateInfo(el, i); });
                        return {
                            ok: false,
                            code: 'ELEMENT_AMBIGUOUS',
                            matchCount: candidates.length,
                            ambiguousCandidates: ambigInfos,
                            diagnostics: {
                                recordedLocator: rawQueue[0] || '',
                                targetLabel: labelHint || $qLabel,
                                totalMatches: candidates.length,
                                visibleMatches: candidates.length,
                                interactableMatches: candidates.filter(function(el) { return !apexIsDisabled(el); }).length,
                                finalOutcome: 'ELEMENT_AMBIGUOUS',
                                attemptedCandidates: attempts
                            },
                            msg: 'Ambiguous locator matched ' + candidates.length + ' elements for "' + (labelHint || $qLabel) + '".'
                        };
                    } else {
                        attempts.push({
                            strategy: 'SCOPED_TEXT',
                            selector: 'label:' + normHint,
                            matchCount: 0,
                            visibleCount: 0,
                            interactableCount: 0,
                            selected: false,
                            rejectedReason: 'NO_MATCH'
                        });
                    }
                }

                if (!chosen) {
                    if (sawAmbiguous) {
                        const ambigInfos = ambiguousElements.map(function(el, i) { return apexCollectCandidateInfo(el, i); });
                        return {
                            ok: false,
                            code: 'ELEMENT_AMBIGUOUS',
                            matchCount: ambiguousCount,
                            ambiguousCandidates: ambigInfos,
                            diagnostics: {
                                recordedLocator: rawQueue[0] || '',
                                targetLabel: labelHint || $qLabel,
                                totalMatches: ambiguousCount,
                                visibleMatches: ambigInfos.filter(function(c) { return c.isVisible; }).length,
                                interactableMatches: ambigInfos.filter(function(c) { return c.isVisible && c.isEnabled; }).length,
                                finalOutcome: 'ELEMENT_AMBIGUOUS',
                                attemptedCandidates: attempts
                            },
                            msg: 'Ambiguous locator matched ' + ambiguousCount + ' elements for "' + (labelHint || $qLabel) + '".'
                        };
                    }
                    if (blockedCode) {
                        return {
                            ok: false,
                            code: blockedCode,
                            msg: blockedMsg,
                            diagnostics: {
                                recordedLocator: rawQueue[0] || '',
                                targetLabel: labelHint || $qLabel,
                                totalMatches: 0,
                                visibleMatches: 0,
                                interactableMatches: 0,
                                finalOutcome: blockedCode,
                                attemptedCandidates: attempts
                            }
                        };
                    }
                    if (apexDetectSecurityChallenge()) {
                        return {
                            ok: false,
                            code: 'SECURITY_CHALLENGE_BLOCKED',
                            msg: 'Page shows an active security challenge or CAPTCHA; manual completion is required.',
                            diagnostics: {
                                recordedLocator: rawQueue[0] || '',
                                targetLabel: labelHint || $qLabel,
                                totalMatches: 0,
                                visibleMatches: 0,
                                interactableMatches: 0,
                                finalOutcome: 'SECURITY_CHALLENGE_BLOCKED',
                                attemptedCandidates: attempts
                            }
                        };
                    }
                    return {
                        ok: false,
                        code: 'ELEMENT_NOT_FOUND',
                        msg: 'Could not find target "' + (labelHint || $qLabel || (selectorQueue.length > 0 ? selectorQueue[0] : '')) + '".',
                        diagnostics: {
                            recordedLocator: rawQueue[0] || '',
                            targetLabel: labelHint || $qLabel,
                            totalMatches: 0,
                            visibleMatches: 0,
                            interactableMatches: 0,
                            finalOutcome: 'ELEMENT_NOT_FOUND',
                            attemptedCandidates: attempts
                        }
                    };
                }

                if (!apexIsConnected(chosen)) {
                    return {
                        ok: false,
                        code: 'ELEMENT_STALE',
                        msg: 'Target element is no longer attached to the DOM.',
                        diagnostics: {
                            recordedLocator: rawQueue[0] || '',
                            targetLabel: labelHint || $qLabel,
                            totalMatches: 1,
                            visibleMatches: 0,
                            interactableMatches: 0,
                            finalOutcome: 'ELEMENT_STALE',
                            attemptedCandidates: attempts
                        }
                    };
                }
                if (!allowOffscreen && !apexIsVisible(chosen)) {
                    return {
                        ok: false,
                        code: 'ELEMENT_NOT_INTERACTABLE',
                        msg: 'Target element "' + (labelHint || $qLabel) + '" is not visible.',
                        diagnostics: {
                            recordedLocator: rawQueue[0] || '',
                            targetLabel: labelHint || $qLabel,
                            totalMatches: 1,
                            visibleMatches: 0,
                            interactableMatches: 0,
                            finalOutcome: 'ELEMENT_NOT_INTERACTABLE',
                            attemptedCandidates: attempts
                        }
                    };
                }
                if (!ignoreDisabled && apexIsDisabled(chosen)) {
                    return {
                        ok: false,
                        code: 'ELEMENT_DISABLED',
                        msg: 'Target element "' + (labelHint || $qLabel) + '" is disabled.',
                        diagnostics: {
                            recordedLocator: rawQueue[0] || '',
                            targetLabel: labelHint || $qLabel,
                            totalMatches: 1,
                            visibleMatches: 1,
                            interactableMatches: 0,
                            finalOutcome: 'ELEMENT_DISABLED',
                            attemptedCandidates: attempts
                        }
                    };
                }
                if (requireWritable && apexIsReadOnly(chosen)) {
                    return {
                        ok: false,
                        code: 'ELEMENT_NOT_INTERACTABLE',
                        msg: 'Target element "' + (labelHint || $qLabel) + '" is read-only.',
                        diagnostics: {
                            recordedLocator: rawQueue[0] || '',
                            targetLabel: labelHint || $qLabel,
                            totalMatches: 1,
                            visibleMatches: 1,
                            interactableMatches: 0,
                            finalOutcome: 'ELEMENT_NOT_INTERACTABLE',
                            attemptedCandidates: attempts
                        }
                    };
                }
                function apexGetElementRect(el) {
                    try {
                        if (!el || !el.getBoundingClientRect) return null;
                        const r = el.getBoundingClientRect();
                        let left = r.left;
                        let top = r.top;
                        let curWin = apexOwnWin(apexOwnDoc(el));
                        let safety = 0;
                        while (curWin && curWin !== window && curWin.frameElement && safety < 5) {
                            const fr = curWin.frameElement.getBoundingClientRect();
                            left += fr.left;
                            top += fr.top;
                            curWin = curWin.parent;
                            safety++;
                        }
                        const vw = window.innerWidth || document.documentElement.clientWidth || 1080;
                        const vh = window.innerHeight || document.documentElement.clientHeight || 1920;
                        return {
                            left: Math.round(left * 10) / 10,
                            top: Math.round(top * 10) / 10,
                            width: Math.round(Math.max(r.width, 1) * 10) / 10,
                            height: Math.round(Math.max(r.height, 1) * 10) / 10,
                            viewportWidth: vw,
                            viewportHeight: vh
                        };
                    } catch (_) {
                        return null;
                    }
                }
                function apexHighlightInteractedElement(el) {
                    try {
                        if (!el || !el.style) return;
                        if (!window.__apexHighlightedElements) {
                            window.__apexHighlightedElements = [];
                        }
                        const prevOutline = el.style.outline || '';
                        const prevOutlineOffset = el.style.outlineOffset || '';
                        const prevBoxShadow = el.style.boxShadow || '';
                        const prevTransition = el.style.transition || '';
                        el.style.transition = 'outline 0.14s ease, box-shadow 0.14s ease';
                        el.style.outline = '2.5px solid #00E5FF';
                        el.style.outlineOffset = '2px';
                        el.style.boxShadow = '0 0 0 4px rgba(0, 229, 255, 0.28), 0 0 18px rgba(0, 229, 255, 0.35)';
                        const entry = {
                            el: el,
                            outline: prevOutline,
                            outlineOffset: prevOutlineOffset,
                            boxShadow: prevBoxShadow,
                            transition: prevTransition
                        };
                        window.__apexHighlightedElements.push(entry);
                        setTimeout(function() {
                            try {
                                if (el && el.style) {
                                    el.style.outline = prevOutline;
                                    el.style.outlineOffset = prevOutlineOffset;
                                    el.style.boxShadow = prevBoxShadow;
                                    el.style.transition = prevTransition;
                                }
                            } catch (_) {}
                        }, 1400);
                    } catch (_) {}
                }
                apexHighlightInteractedElement(chosen);
                const resolvedRect = apexGetElementRect(chosen);
                return {
                    ok: true,
                    el: chosen,
                    doc: apexOwnDoc(chosen),
                    win: apexOwnWin(apexOwnDoc(chosen)),
                    resolvedSelector: matchedVia,
                    recoveredViaFallback: usedFallback,
                    sensitive: apexIsSensitiveEl(chosen),
                    frameChainHint: $qFrameChain,
                    shadowChainHint: $qShadowChain,
                    targetRect: resolvedRect,
                    diagnostics: {
                        recordedLocator: rawQueue[0] || '',
                        targetLabel: labelHint || $qLabel,
                        totalMatches: 1,
                        visibleMatches: 1,
                        interactableMatches: 1,
                        finalOutcome: 'RESOLVED',
                        attemptedCandidates: attempts,
                        targetRect: resolvedRect
                    }
                };
            }
            function apexResolveTarget(sel, labelHint, defaultQuery, opts) {
                const initialList = sel ? [sel] : [];
                return apexResolveWithCandidates(initialList, labelHint, defaultQuery, opts);
            }
        """.trimIndent()
    }

    fun buildStepExecutionScript(step: AutomationStep): String {
        val candidatesJson = buildCandidateSelectorsJson(step)
        val qSel = JSONObject.quote(step.selector)
        val qVal = JSONObject.quote(step.value)
        val qLabel = JSONObject.quote(step.targetLabel)
        val qKey = JSONObject.quote(step.keyboardKey.ifBlank { step.value })
        val desiredCheckedLiteral = when (step.desiredCheckedState) {
            true -> "true"
            false -> "false"
            null -> "null"
        }
        val clearFirstLiteral = if (step.clearBeforeInput) "true" else "false"
        val defaultQuery = when (step.actionType) {
            AutomationActionType.INPUT, AutomationActionType.CLEAR, AutomationActionType.FILL_CREDENTIAL -> when {
                step.targetLabel.contains("email", ignoreCase = true) ->
                    "input[type=\"email\"], input[name*=\"email\"], input, textarea, [contenteditable]"
                step.targetLabel.contains("search", ignoreCase = true) ->
                    "input[type=\"search\"], input[name=\"q\"], input[type=\"text\"], textarea"
                else -> "input:not([type=\"hidden\"]):not([type=\"checkbox\"]):not([type=\"radio\"]):not([type=\"submit\"]):not([type=\"button\"]), textarea, select, [contenteditable=\"true\"], [contenteditable=\"plaintext-only\"], [role=\"textbox\"], [role=\"spinbutton\"], [role=\"combobox\"]"
            }
            AutomationActionType.TOGGLE ->
                "input[type=\"checkbox\"], input[type=\"radio\"], [role=\"checkbox\"], [role=\"switch\"], [role=\"radio\"]"
            AutomationActionType.SELECT ->
                "select, [role=\"combobox\"], [role=\"listbox\"], [role=\"option\"], input:not([type=\"hidden\"]):not([type=\"checkbox\"]):not([type=\"radio\"]):not([type=\"submit\"]):not([type=\"button\"]), textarea"
            AutomationActionType.SUBMIT ->
                "button[type=\"submit\"], input[type=\"submit\"], button, [role=\"button\"]"
            AutomationActionType.CLICK ->
                "button, a[href], summary, input[type=\"button\"], input[type=\"submit\"], [role=\"button\"], [role=\"link\"], [role=\"tab\"], [role=\"menuitem\"]"
            AutomationActionType.FOCUS, AutomationActionType.BLUR, AutomationActionType.KEY_PRESS ->
                "input:not([type=\"hidden\"]), textarea, select, button, a[href], [contenteditable], [tabindex], [role]"
            AutomationActionType.SCROLL ->
                "input, textarea, select, button, a[href], section, form, [role]"
            AutomationActionType.EXTRACT ->
                "article, h1, h2, h3, p, a"
            AutomationActionType.WAIT,
            AutomationActionType.NAVIGATE,
            AutomationActionType.CONDITION,
            AutomationActionType.LOOP,
            AutomationActionType.SET_VARIABLE,
            AutomationActionType.CALL_WORKFLOW,
            AutomationActionType.UNSUPPORTED -> "*"
        }
        val qDefaultQuery = JSONObject.quote(defaultQuery)
        val helpersJs = buildTargetHelpersJs(
            labelHint = step.targetLabel,
            framePath = step.framePath,
            shadowHostPath = step.shadowHostPath
        )

        val actionBody = when (step.actionType) {
            AutomationActionType.SCROLL -> buildScrollScriptBody(qSel, qVal, qLabel, qDefaultQuery)
            AutomationActionType.INPUT -> buildInputScriptBody(qVal, qLabel, qDefaultQuery, clearFirstLiteral)
            AutomationActionType.CLEAR -> buildClearScriptBody(qLabel, qDefaultQuery)
            AutomationActionType.TOGGLE -> buildToggleScriptBody(qLabel, qDefaultQuery, desiredCheckedLiteral)
            AutomationActionType.SELECT -> buildSelectScriptBody(qVal, qLabel, qDefaultQuery)
            AutomationActionType.FOCUS -> buildFocusBlurScriptBody(isFocus = true, qLabel = qLabel, qDefaultQuery = qDefaultQuery)
            AutomationActionType.BLUR -> buildFocusBlurScriptBody(isFocus = false, qLabel = qLabel, qDefaultQuery = qDefaultQuery)
            AutomationActionType.KEY_PRESS -> buildKeyPressScriptBody(qKey, qLabel, qDefaultQuery)
            AutomationActionType.EXTRACT -> buildExtractScriptBody(qSel, qLabel)
            AutomationActionType.CLICK, AutomationActionType.SUBMIT ->
                buildClickOrSubmitScriptBody(isSubmit = step.actionType == AutomationActionType.SUBMIT, qLabel = qLabel, qDefaultQuery = qDefaultQuery)
            AutomationActionType.FILL_CREDENTIAL -> """
                return JSON.stringify({
                    ok: false,
                    code: 'CREDENTIAL_AUTH_REQUIRED',
                    msg: 'Credential fill requires authorized CredentialFillCoordinator execution.'
                });
            """.trimIndent()
            AutomationActionType.NAVIGATE,
            AutomationActionType.WAIT,
            AutomationActionType.CONDITION,
            AutomationActionType.LOOP,
            AutomationActionType.SET_VARIABLE,
            AutomationActionType.CALL_WORKFLOW -> """
                return JSON.stringify({ ok: true, code: 'NONE', msg: 'Handled natively by coordinator.' });
            """.trimIndent()
            AutomationActionType.UNSUPPORTED -> """
                return JSON.stringify({
                    ok: false,
                    code: 'UNSUPPORTED_ACTION',
                    msg: 'Unsupported workflow action type cannot be executed safely.'
                });
            """.trimIndent()
        }

        return """
            (function() {
                try {
                    $helpersJs
                    const CANDIDATE_SELECTORS = $candidatesJson;
                    function apexResolveOrderedTarget(defaultQuery, opts) {
                        const queue = CANDIDATE_SELECTORS.length > 0 ? CANDIDATE_SELECTORS : ($qSel ? [$qSel] : []);
                        if (queue.length === 1) {
                            return apexResolveTarget(queue[0], $qLabel, defaultQuery, opts);
                        }
                        if (queue.length === 0) {
                            return apexResolveTarget('', $qLabel, defaultQuery, opts);
                        }
                        return apexResolveWithCandidates(queue, $qLabel, defaultQuery, opts);
                    }
                    $actionBody
                } catch (err) {
                    return JSON.stringify({
                        ok: false,
                        code: 'JS_EXCEPTION',
                        msg: String(err && err.message ? err.message : err)
                    });
                }
            })();
        """.trimIndent()
    }

    private fun buildScrollScriptBody(
        qSel: String,
        qVal: String,
        qLabel: String,
        qDefaultQuery: String
    ): String = """
        const scrollDir = ($qVal || '').trim().toLowerCase();
        const hasSpecificTarget = CANDIDATE_SELECTORS.length > 0 || Boolean(($qSel || '').trim());
        if (hasSpecificTarget) {
            const resolved = apexResolveOrderedTarget($qDefaultQuery, { allowOffscreen: true, ignoreDisabled: true });
            if (!resolved.ok) return JSON.stringify(resolved);
            const target = resolved.el;
            const win = resolved.win || window;
            if (typeof target.scrollIntoView === 'function') {
                target.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' });
            }
            const rect = target.getBoundingClientRect ? target.getBoundingClientRect() : null;
            const vh = win.innerHeight || 1920;
            const inView = rect ? (rect.bottom >= 0 && rect.top <= vh) : true;
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                inViewport: inView,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Scrolled "' + ($qLabel || 'target') + '" into view.'
            });
        }
        const beforeY = window.scrollY || window.pageYOffset || 0;
        const maxScroll = Math.max(
            0,
            Math.max(
                document.documentElement ? document.documentElement.scrollHeight : 0,
                document.body ? document.body.scrollHeight : 0
            ) - (window.innerHeight || 800)
        );
        if (scrollDir === 'top') {
            window.scrollTo(0, 0);
        } else if (scrollDir === 'bottom') {
            window.scrollTo(0, maxScroll);
        } else {
            const numericDelta = parseInt(scrollDir, 10);
            const deltaY = !isNaN(numericDelta) && numericDelta !== 0 ? numericDelta : (scrollDir === 'up' ? -600 : 600);
            window.scrollBy(0, deltaY);
        }
        const afterY = window.scrollY || window.pageYOffset || 0;
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            moved: afterY !== beforeY,
            scrollY: afterY,
            msg: 'Scrolled page (' + (scrollDir || 'down') + ') to y=' + Math.round(afterY) + '.'
        });
    """.trimIndent()

    private fun buildInputScriptBody(
        qVal: String,
        qLabel: String,
        qDefaultQuery: String,
        clearFirstLiteral: String
    ): String = """
        const resolved = apexResolveOrderedTarget($qDefaultQuery, { requireWritable: true });
        if (!resolved.ok) return JSON.stringify(resolved);
        const target = resolved.el;
        const win = resolved.win || window;
        const tag = (target.tagName || '').toLowerCase();
        const type = (target.getAttribute && target.getAttribute('type') || '').toLowerCase();
        const val = String($qVal);
        if (typeof target.scrollIntoView === 'function') {
            try { target.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' }); } catch (_) {}
        }
        try { target.focus(); } catch (_) {}

        const isDateTimeNative = tag === 'input' && ['date', 'datetime-local', 'time', 'month', 'week'].indexOf(type) >= 0;
        if (isDateTimeNative) {
            const validDate = type !== 'date' || /^\d{4}-\d{2}-\d{2}$/.test(val);
            const validTime = type !== 'time' || /^\d{2}:\d{2}(:\d{2})?$/.test(val);
            const validDateTimeLocal = type !== 'datetime-local' || /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(val);
            const validMonth = type !== 'month' || /^\d{4}-\d{2}$/.test(val);
            const validWeek = type !== 'week' || /^\d{4}-W\d{2}$/.test(val);
            if (!validDate || !validTime || !validDateTimeLocal || !validMonth || !validWeek) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Invalid ' + type + ' value format for "' + $qLabel + '".'
                });
            }
            const minVal = target.getAttribute('min') || '';
            const maxVal = target.getAttribute('max') || '';
            if ((minVal && val < minVal) || (maxVal && val > maxVal)) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Date/time value for "' + $qLabel + '" is outside [' + minVal + '..' + maxVal + '].'
                });
            }
        }

        const ceAttr = (target.getAttribute && target.getAttribute('contenteditable') || '').toLowerCase();
        const isContentEditable = ceAttr === 'true' || ceAttr === 'plaintext-only' || Boolean(target.isContentEditable);

        if (tag === 'select') {
            const opts = Array.from(target.options || []);
            const wanted = apexNorm(val);
            const exact = opts.filter(function(o) {
                return apexNorm(o.value) === wanted || apexNorm(o.text || o.label) === wanted;
            });
            const partial = exact.length > 0 ? exact : opts.filter(function(o) {
                return apexNorm(o.value).indexOf(wanted) >= 0 || apexNorm(o.text || o.label).indexOf(wanted) >= 0;
            });
            if (partial.length === 0) {
                return JSON.stringify({
                    ok: false,
                    code: 'OPTION_NOT_FOUND',
                    msg: 'Could not find option "' + val + '" in "' + $qLabel + '".'
                });
            }
            const chosenOpt = partial[0];
            if (chosenOpt.disabled) {
                return JSON.stringify({
                    ok: false,
                    code: 'ELEMENT_DISABLED',
                    msg: 'Option "' + (chosenOpt.text || chosenOpt.value) + '" is disabled.'
                });
            }
            target.value = chosenOpt.value;
            chosenOpt.selected = true;
            target.dispatchEvent(new Event('input', { bubbles: true }));
            target.dispatchEvent(new Event('change', { bubbles: true }));
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Selected "' + (chosenOpt.text || chosenOpt.value) + '" in "' + $qLabel + '".'
            });
        }

        if (tag !== 'input' && tag !== 'textarea' && !isContentEditable) {
            return JSON.stringify({
                ok: false,
                code: 'ELEMENT_NOT_INTERACTABLE',
                msg: 'Target "' + $qLabel + '" (' + tag + ') does not accept text input.'
            });
        }

        if (isContentEditable && tag !== 'input' && tag !== 'textarea') {
            if ($clearFirstLiteral) {
                target.textContent = '';
            }
            if ('innerText' in target && ceAttr !== 'plaintext-only') {
                target.innerText = val;
            } else {
                target.textContent = val;
            }
            try { target.dispatchEvent(new Event('beforeinput', { bubbles: true, cancelable: true })); } catch (_) {}
            target.dispatchEvent(new Event('input', { bubbles: true }));
            target.dispatchEvent(new Event('change', { bubbles: true }));
            const actualCe = apexNorm(target.innerText || target.textContent || '');
            const expectedCe = apexNorm(val);
            if (expectedCe && actualCe !== expectedCe) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Contenteditable value verification failed for "' + $qLabel + '".'
                });
            }
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Updated rich text "' + $qLabel + '".'
            });
        }

        const proto = tag === 'textarea' ? win.HTMLTextAreaElement && win.HTMLTextAreaElement.prototype : win.HTMLInputElement && win.HTMLInputElement.prototype;
        const descriptor = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
        if ($clearFirstLiteral) {
            if (descriptor && descriptor.set) descriptor.set.call(target, '');
            else target.value = '';
        }
        if (descriptor && descriptor.set) {
            descriptor.set.call(target, val);
        } else {
            target.value = val;
        }
        try { target.dispatchEvent(new Event('beforeinput', { bubbles: true, cancelable: true })); } catch (_) {}
        target.dispatchEvent(new Event('input', { bubbles: true }));
        target.dispatchEvent(new Event('change', { bubbles: true }));

        const retained = String(target.value || '');
        if (retained !== val && !(val.length > 0 && retained.length === val.length)) {
            return JSON.stringify({
                ok: false,
                code: 'VERIFICATION_FAILED',
                msg: 'Input value did not persist in "' + $qLabel + '".'
            });
        }
        const safeEcho = resolved.sensitive ? '••••' : ('"' + val.slice(0, 24) + '"');
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            verified: true,
            resolvedSelector: resolved.resolvedSelector || '',
            recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
            diagnostics: resolved.diagnostics || null,
            msg: 'Entered ' + safeEcho + ' into "' + $qLabel + '".'
        });
    """.trimIndent()

    private fun buildClearScriptBody(
        qLabel: String,
        qDefaultQuery: String
    ): String = """
        const resolved = apexResolveOrderedTarget($qDefaultQuery, { requireWritable: true });
        if (!resolved.ok) return JSON.stringify(resolved);
        const target = resolved.el;
        const win = resolved.win || window;
        const tag = (target.tagName || '').toLowerCase();
        const ceAttr = (target.getAttribute && target.getAttribute('contenteditable') || '').toLowerCase();
        const isContentEditable = ceAttr === 'true' || ceAttr === 'plaintext-only' || Boolean(target.isContentEditable);
        try { target.focus(); } catch (_) {}

        if (isContentEditable && tag !== 'input' && tag !== 'textarea') {
            target.textContent = '';
            target.dispatchEvent(new Event('input', { bubbles: true }));
            target.dispatchEvent(new Event('change', { bubbles: true }));
            if ((target.textContent || '').trim().length > 0) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Failed to clear contenteditable "' + $qLabel + '".'
                });
            }
        } else {
            const proto = tag === 'textarea' ? win.HTMLTextAreaElement && win.HTMLTextAreaElement.prototype : win.HTMLInputElement && win.HTMLInputElement.prototype;
            const descriptor = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
            if (descriptor && descriptor.set) descriptor.set.call(target, '');
            else target.value = '';
            target.dispatchEvent(new Event('input', { bubbles: true }));
            target.dispatchEvent(new Event('change', { bubbles: true }));
            if (String(target.value || '').length > 0) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Input "' + $qLabel + '" still contains text after clear.'
                });
            }
        }
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            verified: true,
            resolvedSelector: resolved.resolvedSelector || '',
            recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
            diagnostics: resolved.diagnostics || null,
            msg: 'Cleared "' + $qLabel + '".'
        });
    """.trimIndent()

    private fun buildToggleScriptBody(
        qLabel: String,
        qDefaultQuery: String,
        desiredCheckedLiteral: String
    ): String = """
        const resolved = apexResolveOrderedTarget($qDefaultQuery, { requireWritable: true });
        if (!resolved.ok) return JSON.stringify(resolved);
        const target = resolved.el;
        const tag = (target.tagName || '').toLowerCase();
        const type = (target.getAttribute && target.getAttribute('type') || '').toLowerCase();
        const role = (target.getAttribute && target.getAttribute('role') || '').toLowerCase();
        const desiredState = $desiredCheckedLiteral;
        const isNativeToggle = tag === 'input' && (type === 'checkbox' || type === 'radio');
        const isAriaToggle = role === 'checkbox' || role === 'switch' || role === 'radio';
        if (!isNativeToggle && !isAriaToggle) {
            return JSON.stringify({
                ok: false,
                code: 'ELEMENT_NOT_INTERACTABLE',
                msg: 'Target "' + $qLabel + '" is not a checkbox, switch, or radio button.'
            });
        }
        if (typeof target.scrollIntoView === 'function') {
            try { target.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' }); } catch (_) {}
        }
        function readState(el) {
            if (isNativeToggle) return Boolean(el.checked);
            return String(el.getAttribute('aria-checked') || '').toLowerCase() === 'true';
        }
        const beforeChecked = readState(target);
        const targetWanted = desiredState !== null ? desiredState : (type === 'radio' || role === 'radio' ? true : !beforeChecked);
        if (beforeChecked !== targetWanted) {
            target.click();
            let afterClick = readState(target);
            if (afterClick !== targetWanted && isNativeToggle) {
                target.checked = targetWanted;
                target.dispatchEvent(new Event('input', { bubbles: true }));
                target.dispatchEvent(new Event('change', { bubbles: true }));
                afterClick = readState(target);
            }
            if (afterClick !== targetWanted) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Toggle state did not become ' + targetWanted + ' for "' + $qLabel + '".'
                });
            }
        }
        const finalState = readState(target);
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            checked: finalState,
            verified: true,
            resolvedSelector: resolved.resolvedSelector || '',
            recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
            diagnostics: resolved.diagnostics || null,
            msg: (finalState ? 'Selected "' : 'Deselected "') + $qLabel + '".'
        });
    """.trimIndent()

    private fun buildSelectScriptBody(
        qVal: String,
        qLabel: String,
        qDefaultQuery: String
    ): String = """
        const resolved = apexResolveOrderedTarget($qDefaultQuery, { requireWritable: true });
        if (!resolved.ok) return JSON.stringify(resolved);
        const target = resolved.el;
        const doc = resolved.doc || document;
        const tag = (target.tagName || '').toLowerCase();
        const role = (target.getAttribute && target.getAttribute('role') || '').toLowerCase();
        const wantedRaw = String($qVal || '').trim();
        const wanted = apexNorm(wantedRaw || $qLabel);
        if (typeof target.scrollIntoView === 'function') {
            try { target.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' }); } catch (_) {}
        }

        const targetType = (target.getAttribute && target.getAttribute('type') || '').toLowerCase();
        if (!wantedRaw && (tag === 'input' || tag === 'textarea' || tag === 'select')) {
            try { target.focus(); } catch (_) {}
            try { target.click(); } catch (_) {}
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Focused "' + $qLabel + '".'
            });
        }
        if (tag === 'input' && ['date', 'datetime-local', 'time', 'month', 'week'].indexOf(targetType) >= 0) {
            const validDate = targetType !== 'date' || /^\d{4}-\d{2}-\d{2}$/.test(wantedRaw);
            const validTime = targetType !== 'time' || /^\d{2}:\d{2}(:\d{2})?$/.test(wantedRaw);
            const validDtLocal = targetType !== 'datetime-local' || /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(wantedRaw);
            const validMonth = targetType !== 'month' || /^\d{4}-\d{2}$/.test(wantedRaw);
            const validWeek = targetType !== 'week' || /^\d{4}-W\d{2}$/.test(wantedRaw);
            if (!wantedRaw || !validDate || !validTime || !validDtLocal || !validMonth || !validWeek) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Invalid ' + targetType + ' value format for "' + $qLabel + '".'
                });
            }
            target.value = wantedRaw;
            target.dispatchEvent(new Event('input', { bubbles: true }));
            target.dispatchEvent(new Event('change', { bubbles: true }));
            if (String(target.value || '') !== wantedRaw) {
                return JSON.stringify({
                    ok: false,
                    code: 'VERIFICATION_FAILED',
                    msg: 'Date/time value did not persist in "' + $qLabel + '".'
                });
            }
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                selectedValue: target.value,
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                msg: 'Set ' + targetType + ' value "' + wantedRaw + '" for "' + $qLabel + '".'
            });
        }

        if ((tag === 'input' || tag === 'textarea') && wantedRaw &&
            ['button', 'submit', 'reset', 'checkbox', 'radio', 'hidden'].indexOf(targetType) < 0 &&
            role !== 'combobox' && !target.getAttribute('aria-controls') && !target.getAttribute('aria-haspopup')) {
            const win = resolved.win || window;
            try { target.focus(); } catch (_) {}
            const proto = tag === 'textarea' ? win.HTMLTextAreaElement && win.HTMLTextAreaElement.prototype : win.HTMLInputElement && win.HTMLInputElement.prototype;
            const descriptor = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
            if (descriptor && descriptor.set) {
                descriptor.set.call(target, wantedRaw);
            } else {
                target.value = wantedRaw;
            }
            try { target.dispatchEvent(new Event('beforeinput', { bubbles: true, cancelable: true })); } catch (_) {}
            target.dispatchEvent(new Event('input', { bubbles: true }));
            target.dispatchEvent(new Event('change', { bubbles: true }));
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                selectedValue: String(target.value || ''),
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Entered "' + wantedRaw + '" into "' + $qLabel + '".'
            });
        }

        if (tag === 'select') {
            const opts = Array.from(target.options || []);
            if (opts.length === 0) {
                return JSON.stringify({ ok: false, code: 'OPTION_NOT_FOUND', msg: 'Select has no options.' });
            }
            let chosenOpt = null;
            if (wanted) {
                const exact = opts.filter(function(o) {
                    return apexNorm(o.value) === wanted || apexNorm(o.text || o.label) === wanted;
                });
                if (exact.length === 1) chosenOpt = exact[0];
                else if (exact.length > 1) {
                    return JSON.stringify({ ok: false, code: 'ELEMENT_AMBIGUOUS', msg: 'Multiple options matched "' + wantedRaw + '".' });
                } else {
                    const partial = opts.filter(function(o) {
                        return apexNorm(o.value).indexOf(wanted) >= 0 || apexNorm(o.text || o.label).indexOf(wanted) >= 0;
                    });
                    if (partial.length === 1) chosenOpt = partial[0];
                    else if (partial.length > 1) {
                        return JSON.stringify({ ok: false, code: 'ELEMENT_AMBIGUOUS', msg: 'Multiple options partially matched "' + wantedRaw + '".' });
                    }
                }
            }
            if (!chosenOpt) {
                return JSON.stringify({ ok: false, code: 'OPTION_NOT_FOUND', msg: 'Could not find option "' + (wantedRaw || $qLabel) + '".' });
            }
            if (chosenOpt.disabled) {
                return JSON.stringify({ ok: false, code: 'ELEMENT_DISABLED', msg: 'Option "' + (chosenOpt.text || chosenOpt.value) + '" is disabled.' });
            }
            target.value = chosenOpt.value;
            chosenOpt.selected = true;
            target.dispatchEvent(new Event('input', { bubbles: true }));
            target.dispatchEvent(new Event('change', { bubbles: true }));
            if (target.value !== chosenOpt.value && !chosenOpt.selected) {
                return JSON.stringify({ ok: false, code: 'VERIFICATION_FAILED', msg: 'Select value did not update.' });
            }
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                selectedValue: target.value,
                selectedText: (chosenOpt.text || '').trim(),
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Selected "' + (chosenOpt.text || chosenOpt.value) + '".'
            });
        }

        if (tag === 'option' || role === 'option' || role === 'menuitem') {
            target.click();
            if (tag === 'option' && target.parentElement && target.parentElement.tagName && target.parentElement.tagName.toLowerCase() === 'select') {
                target.selected = true;
                target.parentElement.dispatchEvent(new Event('change', { bubbles: true }));
            }
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Selected option "' + $qLabel + '".'
            });
        }

        // Custom dropdown / combobox / listbox / custom datepicker
        if (role === 'combobox' || target.getAttribute('aria-controls') || target.getAttribute('aria-haspopup')) {
            const expanded = String(target.getAttribute('aria-expanded') || '').toLowerCase();
            if (expanded !== 'true') {
                target.click();
            }
            const controlsId = target.getAttribute('aria-controls') || '';
            const container = controlsId ? (doc.getElementById(controlsId) || document.getElementById(controlsId)) : null;
            const searchScope = container || doc;
            const optionNodes = Array.from(searchScope.querySelectorAll('[role="option"], [role="menuitem"], [role="gridcell"], option, li[data-value]'));
            let matched = [];
            if (wanted) {
                const exactOpts = optionNodes.filter(function(o) {
                    const txt = apexNorm(o.innerText || o.textContent || o.getAttribute('aria-label') || o.getAttribute('data-value') || '');
                    return txt === wanted;
                });
                matched = exactOpts.length > 0 ? exactOpts : optionNodes.filter(function(o) {
                    const txt = apexNorm(o.innerText || o.textContent || o.getAttribute('aria-label') || o.getAttribute('data-value') || '');
                    return txt.indexOf(wanted) >= 0;
                });
            }
            if (matched.length === 0) {
                return JSON.stringify({
                    ok: false,
                    code: 'OPTION_NOT_FOUND',
                    msg: 'No custom option matched "' + (wantedRaw || $qLabel) + '".'
                });
            }
            if (matched.length > 1) {
                return JSON.stringify({
                    ok: false,
                    code: 'ELEMENT_AMBIGUOUS',
                    msg: 'Multiple custom options matched "' + (wantedRaw || $qLabel) + '".'
                });
            }
            const optEl = matched[0];
            if (apexIsDisabled(optEl)) {
                return JSON.stringify({ ok: false, code: 'ELEMENT_DISABLED', msg: 'Target option is disabled.' });
            }
            optEl.click();
            const confirmedText = (optEl.innerText || optEl.textContent || wantedRaw).trim();
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                selectedText: confirmedText,
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Selected "' + confirmedText + '" from custom control.'
            });
        }

        if (role === 'listbox' || role === 'grid' || target.hasAttribute('data-datepicker')) {
            const optionNodes = Array.from(target.querySelectorAll('[role="option"], [role="gridcell"], [data-date], li[data-value]'));
            const exactOpts = optionNodes.filter(function(o) {
                const txt = apexNorm(o.innerText || o.textContent || o.getAttribute('data-date') || o.getAttribute('aria-label') || o.getAttribute('data-value') || '');
                return txt === wanted;
            });
            const matched = exactOpts.length > 0 ? exactOpts : optionNodes.filter(function(o) {
                const txt = apexNorm(o.innerText || o.textContent || o.getAttribute('data-date') || o.getAttribute('aria-label') || o.getAttribute('data-value') || '');
                return txt.indexOf(wanted) >= 0;
            });
            if (matched.length === 0) {
                return JSON.stringify({ ok: false, code: 'OPTION_NOT_FOUND', msg: 'Option "' + (wantedRaw || $qLabel) + '" not found in control.' });
            }
            if (matched.length > 1) {
                return JSON.stringify({ ok: false, code: 'ELEMENT_AMBIGUOUS', msg: 'Multiple options matched "' + (wantedRaw || $qLabel) + '".' });
            }
            matched[0].click();
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Selected "' + wantedRaw + '".'
            });
        }

        target.click();
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            resolvedSelector: resolved.resolvedSelector || '',
            recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
            diagnostics: resolved.diagnostics || null,
            msg: 'Activated "' + $qLabel + '".'
        });
    """.trimIndent()

    private fun buildFocusBlurScriptBody(
        isFocus: Boolean,
        qLabel: String,
        qDefaultQuery: String
    ): String = """
        const resolved = apexResolveOrderedTarget($qDefaultQuery, {});
        if (!resolved.ok) return JSON.stringify(resolved);
        const target = resolved.el;
        const doc = resolved.doc || document;
        const root = (target.getRootNode && target.getRootNode()) || doc;
        if (typeof target.scrollIntoView === 'function') {
            try { target.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' }); } catch (_) {}
        }
        if (${if (isFocus) "true" else "false"}) {
            if (typeof target.focus !== 'function') {
                return JSON.stringify({ ok: false, code: 'ELEMENT_NOT_INTERACTABLE', msg: 'Element cannot be focused.' });
            }
            target.focus();
            target.dispatchEvent(new Event('focus', { bubbles: false }));
            const activeMatch = (root && root.activeElement === target) || doc.activeElement === target;
            if (!activeMatch && target.tabIndex < 0 && !/^(input|textarea|select|button|a)$/.test((target.tagName || '').toLowerCase())) {
                return JSON.stringify({ ok: false, code: 'VERIFICATION_FAILED', msg: 'Element "' + $qLabel + '" did not receive focus.' });
            }
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                focused: true,
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Focused "' + $qLabel + '".'
            });
        } else {
            if (typeof target.blur === 'function') target.blur();
            target.dispatchEvent(new Event('blur', { bubbles: false }));
            const stillFocused = (root && root.activeElement === target) || doc.activeElement === target;
            if (stillFocused) {
                return JSON.stringify({ ok: false, code: 'VERIFICATION_FAILED', msg: 'Element "' + $qLabel + '" remained focused after blur.' });
            }
            return JSON.stringify({
                ok: true,
                code: 'NONE',
                blurred: true,
                verified: true,
                resolvedSelector: resolved.resolvedSelector || '',
                recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
                diagnostics: resolved.diagnostics || null,
                msg: 'Blurred "' + $qLabel + '".'
            });
        }
    """.trimIndent()

    private fun buildKeyPressScriptBody(
        qKey: String,
        qLabel: String,
        qDefaultQuery: String
    ): String = """
        const rawKey = String($qKey || '').trim() || 'Enter';
        const lowerKey = rawKey.toLowerCase();
        const keyMap = {
            'enter': { key: 'Enter', code: 'Enter', keyCode: 13 },
            'return': { key: 'Enter', code: 'Enter', keyCode: 13 },
            'escape': { key: 'Escape', code: 'Escape', keyCode: 27 },
            'esc': { key: 'Escape', code: 'Escape', keyCode: 27 },
            'tab': { key: 'Tab', code: 'Tab', keyCode: 9 },
            'space': { key: ' ', code: 'Space', keyCode: 32 },
            ' ': { key: ' ', code: 'Space', keyCode: 32 },
            'arrowdown': { key: 'ArrowDown', code: 'ArrowDown', keyCode: 40 },
            'down': { key: 'ArrowDown', code: 'ArrowDown', keyCode: 40 },
            'arrowup': { key: 'ArrowUp', code: 'ArrowUp', keyCode: 38 },
            'up': { key: 'ArrowUp', code: 'ArrowUp', keyCode: 38 },
            'arrowleft': { key: 'ArrowLeft', code: 'ArrowLeft', keyCode: 37 },
            'left': { key: 'ArrowLeft', code: 'ArrowLeft', keyCode: 37 },
            'arrowright': { key: 'ArrowRight', code: 'ArrowRight', keyCode: 39 },
            'right': { key: 'ArrowRight', code: 'ArrowRight', keyCode: 39 },
            'backspace': { key: 'Backspace', code: 'Backspace', keyCode: 8 }
        };
        const spec = keyMap[lowerKey] || { key: rawKey, code: rawKey, keyCode: rawKey.charCodeAt(0) || 0 };
        let target = null;
        let resolvedMeta = { resolvedSelector: '', recoveredViaFallback: false };
        if (CANDIDATE_SELECTORS.length > 0 || ($qLabel || '').trim().length > 0) {
            const resolved = apexResolveOrderedTarget($qDefaultQuery, {});
            if (!resolved.ok) return JSON.stringify(resolved);
            target = resolved.el;
            resolvedMeta = resolved;
        } else {
            target = document.activeElement || document.body;
        }
        if (!target) {
            return JSON.stringify({ ok: false, code: 'ELEMENT_NOT_FOUND', msg: 'No target element available for key press.' });
        }
        try { if (typeof target.focus === 'function') target.focus(); } catch (_) {}
        const evInit = { key: spec.key, code: spec.code, keyCode: spec.keyCode, which: spec.keyCode, bubbles: true, cancelable: true };
        const downNotCanceled = target.dispatchEvent(new KeyboardEvent('keydown', evInit));
        target.dispatchEvent(new KeyboardEvent('keypress', evInit));
        target.dispatchEvent(new KeyboardEvent('keyup', evInit));

        if (downNotCanceled) {
            const tag = (target.tagName || '').toLowerCase();
            if (spec.key === 'Enter') {
                if (tag === 'button' || tag === 'a' || target.getAttribute('role') === 'button') {
                    target.click();
                } else if (target.form && !target.form.dataset.apexSubmitted) {
                    target.form.dataset.apexSubmitted = '1';
                    if (typeof target.form.requestSubmit === 'function') target.form.requestSubmit();
                    else target.form.submit();
                }
            } else if (spec.key === ' ' && (tag === 'button' || tag === 'input' || target.getAttribute('role') === 'checkbox' || target.getAttribute('role') === 'button')) {
                target.click();
            } else if (spec.key === 'Escape' && target.getAttribute('aria-expanded') === 'true') {
                target.setAttribute('aria-expanded', 'false');
            }
        }
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            key: spec.key,
            verified: true,
            resolvedSelector: resolvedMeta.resolvedSelector || '',
            recoveredViaFallback: Boolean(resolvedMeta.recoveredViaFallback),
            diagnostics: resolvedMeta.diagnostics || null,
            msg: 'Pressed ' + spec.key + ' on "' + ($qLabel || 'active element') + '".'
        });
    """.trimIndent()

    private fun buildClickOrSubmitScriptBody(
        isSubmit: Boolean,
        qLabel: String,
        qDefaultQuery: String
    ): String = """
        const resolved = apexResolveOrderedTarget($qDefaultQuery, {});
        if (!resolved.ok) return JSON.stringify(resolved);
        const target = resolved.el;
        const doc = resolved.doc || document;
        const tag = (target.tagName || '').toLowerCase();
        const form = target.form || (target.closest ? target.closest('form') : null);
        if (${if (isSubmit) "true" else "false"}) {
            if (target.dataset && target.dataset.apexSubmitted === '1') {
                return JSON.stringify({ ok: false, code: 'DUPLICATE_EXECUTION', msg: 'Prevented duplicate form submission for "' + $qLabel + '".' });
            }
            if (form && form.dataset && form.dataset.apexSubmitted === '1') {
                return JSON.stringify({ ok: false, code: 'DUPLICATE_EXECUTION', msg: 'Form was already submitted in this page session.' });
            }
        }
        if (typeof target.scrollIntoView === 'function') {
            try { target.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' }); } catch (_) {}
        }
        const preExpanded = target.getAttribute ? target.getAttribute('aria-expanded') : null;
        const prePressed = target.getAttribute ? target.getAttribute('aria-pressed') : null;
        const preSelected = target.getAttribute ? target.getAttribute('aria-selected') : null;
        const preChecked = typeof target.checked === 'boolean' ? target.checked : null;
        const preHref = tag === 'a' ? (target.getAttribute('href') || '') : '';
        const preCount = doc.body ? doc.body.getElementsByTagName('*').length : 0;

        try { if (typeof target.focus === 'function') target.focus(); } catch (_) {}
        if (${if (isSubmit) "true" else "false"}) {
            if (target.dataset) target.dataset.apexSubmitted = '1';
            if (form && form.dataset) form.dataset.apexSubmitted = '1';
            if (tag === 'form') {
                if (typeof target.requestSubmit === 'function') target.requestSubmit();
                else target.submit();
            } else {
                target.click();
            }
        } else {
            target.click();
        }

        const postExpanded = target.getAttribute ? target.getAttribute('aria-expanded') : null;
        const postPressed = target.getAttribute ? target.getAttribute('aria-pressed') : null;
        const postSelected = target.getAttribute ? target.getAttribute('aria-selected') : null;
        const postChecked = typeof target.checked === 'boolean' ? target.checked : null;
        const postCount = doc.body ? doc.body.getElementsByTagName('*').length : 0;
        const stateDelta = (preExpanded !== postExpanded) || (prePressed !== postPressed) ||
            (preSelected !== postSelected) || (preChecked !== postChecked) ||
            (preCount !== postCount) || Boolean(preHref) || Boolean(form);

        return JSON.stringify({
            ok: true,
            code: 'NONE',
            verified: true,
            stateChanged: stateDelta,
            mayTriggerNavigation: Boolean(preHref || ${if (isSubmit) "true" else "false"} || (tag === 'button' && ((target.getAttribute('type') || 'submit').toLowerCase() === 'submit') && Boolean(form))),
            resolvedSelector: resolved.resolvedSelector || '',
            recoveredViaFallback: Boolean(resolved.recoveredViaFallback),
            diagnostics: resolved.diagnostics || null,
            msg: '${if (isSubmit) "Submitted" else "Activated"} "' + $qLabel + '".'
        });
    """.trimIndent()

    private fun buildExtractScriptBody(qSel: String, qLabel: String): String = """
        const q = String($qSel || '').trim() || 'h1, h2, h3, article, p';
        const nodes = apexDeepQueryAll(q)
            .filter(function(el) { return apexIsConnected(el) && apexIsVisible(el) && !apexIsSensitiveEl(el); })
            .slice(0, 12);
        const extracted = nodes.map(function(n) {
            return (n.innerText || n.textContent || '').replace(/\s+/g, ' ').trim();
        }).filter(function(t) { return t.length > 0; });
        return JSON.stringify({
            ok: true,
            code: 'NONE',
            count: extracted.length,
            msg: 'Extracted ' + extracted.length + ' text items for "' + $qLabel + '".'
        });
    """.trimIndent()

    fun buildWaitProbeScript(condition: WaitConditionSpec): String {
        val qSel = JSONObject.quote(condition.selector)
        val qLabel = JSONObject.quote(condition.targetLabel)
        val qAttr = JSONObject.quote(condition.attributeName)
        val qAttrVal = JSONObject.quote(condition.expectedAttributeValue)
        val qText = JSONObject.quote(condition.expectedText)
        val qCondType = JSONObject.quote(condition.type.id)
        val helpers = buildTargetHelpersJs(condition.targetLabel)
        return """
            (function() {
                try {
                    $helpers
                    const condType = $qCondType;
                    const sel = $qSel;
                    const labelHint = $qLabel;
                    const docUrl = String(document.URL || window.location && window.location.href || '');
                    const ready = String(document.readyState || 'loading');
                    const domCount = document.body ? document.body.getElementsByTagName('*').length : 0;
                    const textLen = document.body ? String(document.body.innerText || document.body.textContent || '').trim().length : 0;
                    const sig = ready + ':' + domCount + ':' + textLen;

                    if (condType === 'CONTENT_STABILIZED') {
                        return JSON.stringify({
                            satisfied: ready === 'complete' && Boolean(document.body),
                            readyState: ready,
                            url: docUrl,
                            signature: sig
                        });
                    }
                    if (condType === 'URL_CHANGE' || condType === 'ORIGIN_CHANGE' || condType === 'NAVIGATION_COMPLETED') {
                        return JSON.stringify({
                            satisfied: ready === 'complete',
                            readyState: ready,
                            url: docUrl,
                            signature: sig
                        });
                    }
                    if (condType === 'DOM_STATE_CHANGE') {
                        const expectedText = $qText;
                        const attrName = $qAttr;
                        const attrExpected = $qAttrVal;
                        if (sel || labelHint) {
                            const res = apexResolveTarget(sel, labelHint, '*', { allowOffscreen: true, ignoreDisabled: true });
                            if (!res.ok) return JSON.stringify({ satisfied: false, reason: res.msg || res.code, url: docUrl, signature: sig });
                            const el = res.el;
                            let ok = true;
                            if (attrName) {
                                const actualAttr = String(el.getAttribute ? (el.getAttribute(attrName) || '') : '');
                                ok = attrExpected ? (actualAttr === attrExpected) : actualAttr.length > 0;
                            }
                            if (ok && expectedText) {
                                const actualTxt = String(el.value || el.innerText || el.textContent || '');
                                ok = actualTxt.toLowerCase().indexOf(expectedText.toLowerCase()) >= 0;
                            }
                            return JSON.stringify({ satisfied: ok, url: docUrl, signature: sig });
                        } else {
                            const bodyTxt = document.body ? String(document.body.innerText || document.body.textContent || '') : '';
                            const ok = expectedText ? (bodyTxt.toLowerCase().indexOf(expectedText.toLowerCase()) >= 0) : (ready === 'complete');
                            return JSON.stringify({ satisfied: ok, url: docUrl, signature: sig });
                        }
                    }

                    const matches = sel ? apexQueryChain(sel) : { ok: true, matches: apexDeepQueryAll('*') };
                    const connected = (matches.matches || []).filter(function(el) {
                        if (!apexIsConnected(el)) return false;
                        if (!sel && labelHint) {
                            return apexResolveLabel(el).indexOf(apexNorm(labelHint)) >= 0;
                        }
                        return true;
                    });

                    if (condType === 'ELEMENT_DISAPPEARS') {
                        const anyVisible = connected.some(function(el) { return apexIsVisible(el); });
                        return JSON.stringify({ satisfied: !anyVisible, matchCount: connected.length, url: docUrl, signature: sig });
                    }
                    if (condType === 'ELEMENT_APPEARS') {
                        const anyVisible = connected.some(function(el) { return apexIsVisible(el); });
                        return JSON.stringify({ satisfied: anyVisible, matchCount: connected.length, url: docUrl, signature: sig });
                    }
                    if (condType === 'ELEMENT_INTERACTABLE') {
                        const anyReady = connected.some(function(el) { return apexIsVisible(el) && !apexIsDisabled(el); });
                        return JSON.stringify({ satisfied: anyReady, matchCount: connected.length, url: docUrl, signature: sig });
                    }
                    return JSON.stringify({ satisfied: false, url: docUrl, signature: sig });
                } catch (e) {
                    return JSON.stringify({ satisfied: false, error: String(e && e.message ? e.message : e) });
                }
            })();
        """.trimIndent()
    }

    fun buildHighlightSelectorScript(
        selector: String,
        targetLabel: String = "",
        scrollIntoView: Boolean = true
    ): String {
        val qSel = JSONObject.quote(selector)
        val qLabel = JSONObject.quote(targetLabel)
        val scrollLit = if (scrollIntoView) "true" else "false"
        return """
            (function() {
                try {
                    ${buildTargetHelpersJs(targetLabel)}
                    const resolved = apexResolveTarget($qSel, $qLabel, '*', { allowOffscreen: true, ignoreDisabled: true });
                    if (!resolved.ok) {
                        return JSON.stringify({ ok: false, code: resolved.code || 'ELEMENT_NOT_FOUND', msg: resolved.msg || 'Element not found' });
                    }
                    const target = resolved.el;
                    if ($scrollLit && target.scrollIntoView) {
                        target.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
                    }
                    apexHighlightInteractedElement(target);
                    const rect = apexGetElementRect(target);
                    return JSON.stringify({
                        ok: true,
                        resolvedSelector: resolved.resolvedSelector || $qSel,
                        tagName: (target.tagName || '').toLowerCase(),
                        targetRect: rect
                    });
                } catch (e) {
                    return JSON.stringify({ ok: false, error: String(e && e.message ? e.message : e) });
                }
            })();
        """.trimIndent()
    }

    fun buildClearHighlightsScript(): String {
        return """
            (function() {
                try {
                    const list = window.__apexHighlightedElements || [];
                    for (let i = 0; i < list.length; i++) {
                        const item = list[i];
                        if (item && item.el && item.el.style) {
                            item.el.style.outline = item.outline || '';
                            item.el.style.outlineOffset = item.outlineOffset || '';
                            item.el.style.boxShadow = item.boxShadow || '';
                            item.el.style.transition = item.transition || '';
                        }
                    }
                    window.__apexHighlightedElements = [];
                    return JSON.stringify({ ok: true });
                } catch (e) {
                    return JSON.stringify({ ok: false });
                }
            })();
        """.trimIndent()
    }
}
