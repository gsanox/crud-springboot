/* Código común de los apuntes y lecciones del proyecto crud · SASE Formación.
   Se carga con <script src> antes del script propio de cada página, así que lo que
   se declara aquí (LECCIONES, LAYER, renderCode, crearTest...) está disponible en ellas. */

const REPO = 'https://github.com/gsanox/crud-springboot';

/* Itinerario: el índice y la navegación ← → salen de esta lista. */
const LECCIONES = [
  { id: 'mvc', num: '00', titulo: 'MVC explicado paso a paso', url: 'mvc.html',
    problema: 'Qué hace cada capa y cómo viaja una petición desde Postman hasta PostgreSQL y vuelta.',
    chips: ['Controller', 'Service', 'Repository', 'JPA'] },
  { id: 'servicio', num: '01', titulo: 'Capa de servicio', url: 'servicio.html',
    problema: '¿Dónde van las reglas del negocio? Nombre único, ventas con control de stock y transacciones.',
    chips: ['Reglas de negocio', '@Transactional', 'Consultas derivadas'] },
  { id: 'dto', num: '02', titulo: 'Patrón DTO', url: 'dto.html',
    problema: 'La API exponía la entidad tal cual y no validaba nada. Un objeto para lo que entra y otro para lo que sale.',
    chips: ['record', '@Valid', 'Validación'] },
  { id: 'mappers', num: '03', titulo: 'Mappers', url: 'mappers.html',
    problema: 'Convertir entidad ↔ DTO en un único sitio, y por qué actualizar no es lo mismo que crear.',
    chips: ['ProductoMapper', 'toEntity', 'updateEntity'] },
  { id: 'excepciones', num: '04', titulo: 'Tratamiento de excepciones', url: 'excepciones.html',
    problema: 'Cada error salía con un formato distinto, o como un 500. Un manejador global y un JSON de error común.',
    chips: ['@RestControllerAdvice', '@ExceptionHandler', '400 · 404 · 409'] },
  { id: 'cache', num: '05', titulo: 'Caché', url: 'cache.html',
    problema: 'Leer lo mismo una y otra vez de la base de datos, y el problema de verdad: mantener los datos al día.',
    chips: ['@Cacheable', '@CachePut', '@CacheEvict'] }
];

/* Colores de cada capa (definidos en sase.css) */
const LAYER = {
  cliente:    { c: 'var(--c-cliente)',    t: 'var(--tint-cliente)',    name: 'Cliente' },
  controller: { c: 'var(--c-controller)', t: 'var(--tint-controller)', name: 'Controller' },
  service:    { c: 'var(--c-service)',    t: 'var(--tint-service)',    name: 'Service' },
  repository: { c: 'var(--c-repository)', t: 'var(--tint-repository)', name: 'Repository' },
  bd:         { c: 'var(--c-bd)',         t: 'var(--tint-bd)',         name: 'PostgreSQL' },
  model:      { c: 'var(--c-model)',      t: 'var(--tint-model)',      name: 'Modelo' },
  other:      { c: 'var(--c-cliente)',    t: 'var(--tint-cliente)',    name: 'Otro' }
};

/* Pinta un bloque de código con el nombre del fichero y las líneas indicadas resaltadas.
   Usa textContent: el código nunca se interpreta como HTML. */
function renderCode(container, file, lines, hl) {
  container.innerHTML = '';
  const head = document.createElement('div');
  head.className = 'file';
  head.textContent = file;
  const pre = document.createElement('pre');
  lines.forEach((line, i) => {
    const span = document.createElement('span');
    span.className = 'ln' + ((hl || []).includes(i) ? ' hl' : '');
    span.textContent = line;
    pre.appendChild(span);
  });
  container.append(head, pre);
}

/* Progreso del alumno, guardado solo en su navegador.
   Si el navegador no deja guardar (modo privado...), la página funciona igual. */
const Progreso = {
  clave: 'sase-crud-progreso',
  leer() {
    try { return JSON.parse(localStorage.getItem(this.clave)) || {}; } catch (e) { return {}; }
  },
  guardar(id, datos) {
    try {
      const todo = this.leer();
      todo[id] = Object.assign({}, todo[id], datos);
      localStorage.setItem(this.clave, JSON.stringify(todo));
    } catch (e) { /* sin almacenamiento: no se recuerda el progreso */ }
  }
};

/* Autoevaluación: una tarjeta por pregunta; al terminar se guarda la nota de la lección. */
function crearTest(preguntas, opciones) {
  const quiz = opciones.quiz;
  const score = opciones.score;
  let answered = 0, right = 0;

  preguntas.forEach((item, qi) => {
    const card = document.createElement('div');
    card.className = 'card q';
    const h = document.createElement('h3');
    h.textContent = (qi + 1) + '. ' + item.q;
    const opts = document.createElement('div');
    opts.className = 'opts';
    const fb = document.createElement('div');
    fb.className = 'fb';
    const btns = item.opts.map((o, oi) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'opt';
      b.textContent = o;
      b.addEventListener('click', () => {
        btns.forEach(x => x.disabled = true);
        btns[item.ok].classList.add('right');
        const good = oi === item.ok;
        if (!good) b.classList.add('wrong');
        answered++; if (good) right++;
        fb.innerHTML = (good ? '<b style="color:var(--ok)">✔ Correcto.</b> ' : '<b style="color:var(--err)">✘ No exactamente.</b> ') + item.why;
        fb.classList.add('show');
        const fin = answered === preguntas.length;
        score.textContent = 'Llevas ' + right + ' de ' + answered + (fin ? ' · ¡terminado!' : '');
        if (fin && opciones.leccion) {
          Progreso.guardar(opciones.leccion, { test: { aciertos: right, total: preguntas.length } });
        }
      });
      opts.appendChild(b);
      return b;
    });
    card.append(h, opts, fb);
    quiz.appendChild(card);
  });
}

/* Marca la lección como vista y pinta los enlaces a la anterior y la siguiente. */
function iniciarLeccion(id) {
  Progreso.guardar(id, { visto: true });

  const nav = document.getElementById('lessonNav');
  if (!nav) return;
  const i = LECCIONES.findIndex(l => l.id === id);
  const anterior = LECCIONES[i - 1];
  const siguiente = LECCIONES[i + 1];

  function enlace(clase, etiqueta, titulo, href) {
    const a = document.createElement('a');
    a.className = clase;
    a.href = href;
    const small = document.createElement('small');
    small.textContent = etiqueta;
    const span = document.createElement('span');
    span.textContent = titulo;
    a.append(small, span);
    nav.appendChild(a);
  }

  if (anterior) {
    enlace('anterior', '← Lección ' + anterior.num, anterior.titulo, anterior.url);
  } else {
    enlace('anterior', '← Índice', 'Todas las lecciones', 'index.html');
  }
  if (siguiente) {
    enlace('siguiente', 'Lección ' + siguiente.num + ' →', siguiente.titulo, siguiente.url);
  } else {
    enlace('siguiente', 'Índice →', 'Todas las lecciones', 'index.html');
  }
}

/* Juego "¿dónde va cada cosa?": cada frase se asigna a una de las opciones. */
function crearClasifica(contenedor, opciones, items, marcador) {
  let respondidas = 0, aciertos = 0;
  items.forEach(item => {
    const fila = document.createElement('div');
    fila.className = 'item';
    const frase = document.createElement('div');
    frase.className = 'frase';
    frase.textContent = item.frase;
    const grupo = document.createElement('div');
    grupo.className = 'opciones';
    const porque = document.createElement('div');
    porque.className = 'porque';
    const botones = opciones.map(op => {
      const b = document.createElement('button');
      b.type = 'button';
      b.textContent = op;
      b.addEventListener('click', () => {
        botones.forEach(x => x.disabled = true);
        botones[opciones.indexOf(item.ok)].classList.add('right');
        const bien = op === item.ok;
        if (!bien) b.classList.add('wrong');
        respondidas++; if (bien) aciertos++;
        porque.innerHTML = (bien ? '<b style="color:var(--ok)">✔</b> ' : '<b style="color:var(--err)">✘ Va en ' + item.ok + '.</b> ') + item.porque;
        porque.classList.add('show');
        if (marcador) marcador.textContent = aciertos + ' de ' + respondidas + (respondidas === items.length ? ' · ¡terminado!' : '');
      });
      grupo.appendChild(b);
      return b;
    });
    fila.append(frase, grupo, porque);
    contenedor.appendChild(fila);
  });
}

/* Fecha y hora locales en el formato de LocalDateTime (2026-10-23T20:01:36), como las devuelve la API */
function ahoraLocal() {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
}
