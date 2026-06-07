// ── MACRO SYSTEM ──
const MacroSystem = {

  macros: [],

  // Load saved macros
  load() {
    try {
      const saved = window.Android
        ? Android.loadProfile()
        : localStorage.getItem('gg_macros');
      if (saved) {
        const p = JSON.parse(saved);
        if (p.macros) this.macros = p.macros;
      }
    } catch(e) {}
    // Default macros if empty
    if (this.macros.length === 0) {
      this.macros = [
        {
          id: 1,
          name: 'Rush + Spray',
          triggerKey: 'F1',
          icon: '⚡',
          enabled: true,
          steps: [
            { action: 'tap', x: 20, y: 75, delay: 0 },
            { action: 'tap', x: 80, y: 55, delay: 60 },
            { action: 'tap', x: 80, y: 55, delay: 100 },
            { action: 'tap', x: 80, y: 55, delay: 100 },
          ]
        },
        {
          id: 2,
          name: 'Botiquín Rápido',
          triggerKey: 'F2',
          icon: '💉',
          enabled: true,
          steps: [
            { action: 'tap', x: 92, y: 55, delay: 0 },
            { action: 'tap', x: 92, y: 55, delay: 250 },
          ]
        },
        {
          id: 3,
          name: 'Agachar + Disparo',
          triggerKey: 'F3',
          icon: '🎯',
          enabled: true,
          steps: [
            { action: 'tap', x: 90, y: 65, delay: 0 },
            { action: 'tap', x: 80, y: 55, delay: 80 },
            { action: 'tap', x: 80, y: 55, delay: 80 },
            { action: 'tap', x: 80, y: 55, delay: 80 },
            { action: 'tap', x: 90, y: 65, delay: 200 },
          ]
        },
        {
          id: 4,
          name: 'Recargar + Agachar',
          triggerKey: 'F4',
          icon: '🔄',
          enabled: false,
          steps: [
            { action: 'tap', x: 75, y: 70, delay: 0 },
            { action: 'tap', x: 90, y: 65, delay: 150 },
          ]
        },
      ];
    }
  },

  // Save macros
  save() {
    try {
      const saved = window.Android
        ? Android.loadProfile()
        : localStorage.getItem('gg_macros') || '{}';
      const p = JSON.parse(saved);
      p.macros = this.macros;
      const json = JSON.stringify(p);
      if (window.Android) Android.saveProfile(json);
      else localStorage.setItem('gg_macros', json);
    } catch(e) {}
  },

  // Run macro by trigger key
  runByKey(key) {
    const macro = this.macros.find(
      m => m.triggerKey.toUpperCase() === key.toUpperCase() && m.enabled
    );
    if (macro) {
      this.run(macro);
      return true;
    }
    return false;
  },

  // Run a macro
  run(macro) {
    showToast(`⚡ ${macro.name}`);
    if (window.Android) {
      // Run via native Android
      const stepsJson = JSON.stringify(macro.steps);
      Android.runMacro(stepsJson);
    } else {
      // Simulate in browser
      macro.steps.forEach((step, i) => {
        setTimeout(() => {
          showToast(`▶ Paso ${i+1}: ${step.action}`);
        }, step.delay + i * 50);
      });
    }
  },

  // Add new macro
  add(name, triggerKey, icon) {
    const id = Date.now();
    this.macros.push({
      id, name, triggerKey, icon: icon || '⚡',
      enabled: true, steps: []
    });
    this.save();
    return id;
  },

  // Delete macro
  delete(id) {
    const i = this.macros.findIndex(m => m.id === id);
    if (i !== -1) this.macros.splice(i, 1);
    this.save();
  },

  // Toggle macro on/off
  toggle(id) {
    const m = this.macros.find(x => x.id === id);
    if (m) { m.enabled = !m.enabled; this.save(); }
    return m?.enabled;
  },

  // Add step to macro
  addStep(macroId, action, x, y, delay) {
    const m = this.macros.find(x => x.id === macroId);
    if (m) {
      m.steps.push({ action, x, y, delay });
      this.save();
    }
  },

  // Delete step
  deleteStep(macroId, stepIndex) {
    const m = this.macros.find(x => x.id === macroId);
    if (m) {
      m.steps.splice(stepIndex, 1);
      this.save();
    }
  },

  // Set trigger key
  setTriggerKey(macroId, key) {
    const m = this.macros.find(x => x.id === macroId);
    if (m) {
      m.triggerKey = key;
      this.save();
    }
  },

  // Render macros page
  render() {
    const list = document.getElementById('macroListEl');
    if (!list) return;
    if (this.macros.length === 0) {
      list.innerHTML = `
        <div style="padding:40px 20px;text-align:center;color:#999">
          <div style="font-size:40px;margin-bottom:12px">⚡</div>
          <div style="font-size:16px;font-weight:600;margin-bottom:6px">Sin macros</div>
          <div style="font-size:13px">Toca + NUEVA para crear tu primer macro</div>
        </div>`;
      return;
    }
    list.innerHTML = this.macros.map(m => `
      <div class="mac-card" id="mac-${m.id}">
        <div class="mac-head" onclick="MacroSystem.toggleBody(${m.id})">
          <div class="mac-ico">${m.icon}</div>
          <div class="mac-info">
            <div class="mac-name">${m.name}</div>
            <div class="mac-sub">
              <span class="mac-key-badge" onclick="MacroSystem.openKeyAssign(${m.id},event)">${m.triggerKey || '?'}</span>
              · ${m.steps.length} pasos
            </div>
          </div>
          <div class="mac-ctrl">
            <button class="mac-run" onclick="MacroSystem.run(MacroSystem.macros.find(x=>x.id===${m.id}));event.stopPropagation()">▶ RUN</button>
            <button class="mac-tog ${m.enabled?'on':''}" onclick="MacroSystem.toggleMac(${m.id},event)"></button>
          </div>
        </div>
        <div class="mac-body" id="mb-${m.id}">
          <div class="mac-key-row">
            <span style="font-size:13px;color:#666;font-weight:600">Tecla asignada:</span>
            <button class="mac-assign-key" onclick="MacroSystem.openKeyAssign(${m.id},event)">${m.triggerKey || 'Asignar'}</button>
          </div>
          <div class="steps-list" id="sl-${m.id}">
            ${m.steps.map((s,i) => `
              <div class="step-row">
                <span class="step-n">${i+1}</span>
                <span class="step-type">${s.action}</span>
                <span class="step-pos">x:${Math.round(s.x)}% y:${Math.round(s.y)}%</span>
                <span class="step-d">+${s.delay}ms</span>
                <button class="step-x" onclick="MacroSystem.deleteStep(${m.id},${i});MacroSystem.render()">✕</button>
              </div>`).join('')}
          </div>
          <div class="step-add-row">
            <select class="step-sel" id="sa-${m.id}">
              <option value="tap">Toque (tap)</option>
              <option value="swipe">Deslizar</option>
              <option value="key">Tecla</option>
            </select>
            <input class="step-inp" id="sx-${m.id}" type="number" placeholder="X%" value="50" min="0" max="100">
            <input class="step-inp" id="sy-${m.id}" type="number" placeholder="Y%" value="50" min="0" max="100">
            <input class="step-inp" id="sd-${m.id}" type="number" placeholder="ms" value="100" min="0">
            <button class="step-add-btn" onclick="MacroSystem.addStepUI(${m.id})">+ADD</button>
          </div>
          <button class="mac-del-btn" onclick="MacroSystem.deleteMacro(${m.id})">🗑️ Eliminar macro</button>
        </div>
      </div>`).join('');
  },

  toggleBody(id) {
    document.getElementById('mb-'+id)?.classList.toggle('open');
  },

  toggleMac(id, e) {
    e.stopPropagation();
    const on = this.toggle(id);
    this.render();
    showToast(on ? '⚡ Macro activado' : '⛔ Macro desactivado');
  },

  addStepUI(macroId) {
    const action = document.getElementById('sa-'+macroId)?.value || 'tap';
    const x = parseFloat(document.getElementById('sx-'+macroId)?.value) || 50;
    const y = parseFloat(document.getElementById('sy-'+macroId)?.value) || 50;
    const delay = parseInt(document.getElementById('sd-'+macroId)?.value) || 0;
    this.addStep(macroId, action, x, y, delay);
    this.render();
    document.getElementById('mb-'+macroId)?.classList.add('open');
    showToast('✓ Paso añadido');
  },

  deleteMacro(id) {
    if (!confirm('¿Eliminar este macro?')) return;
    this.delete(id);
    this.render();
    showToast('🗑️ Macro eliminado');
  },

  openKeyAssign(macroId, e) {
    if (e) e.stopPropagation();
    currentMacroKeyId = macroId;
    const m = this.macros.find(x => x.id === macroId);
    document.getElementById('kmSub').textContent =
      `Presiona la tecla para activar: ${m?.name}`;
    document.getElementById('kmKey').textContent = m?.triggerKey || '?';
    capturedKey = m?.triggerKey || null;
    modalMode = 'macro';
    modalTarget = null;
    document.getElementById('keyModal').classList.add('show');
  },

  newMacro() {
    const name = prompt('Nombre del macro:');
    if (!name) return;
    const id = this.add(name, '?', '⚡');
    this.render();
    // Open key assign immediately
    setTimeout(() => this.openKeyAssign(id), 100);
    showToast('✓ Macro creado — asigna una tecla');
  }
};

let currentMacroKeyId = null;