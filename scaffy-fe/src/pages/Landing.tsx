import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { AppFrame, Badge, Eyebrow } from '../components'

const initializerPoints = [
  'Choose Angular, Spring Boot, Docker, and a CI provider without memorizing project boilerplate.',
  'Receive a runnable ZIP with project structure, README guidance, and pipeline configuration already wired.',
  'Start with conventions that are useful for students, prototypes, and teams that want a clean first commit.',
]

const analyzerSignals = [
  'Build & Release Management',
  'Testing Maturity',
  'Security Integration',
  'Deployment Automation',
  'Workflow Quality & Optimization',
]

const modelSources = [
  {
    name: 'LADMF',
    role: 'Maturity skeleton',
    detail:
      'Provides the five-dimension grouping (build, test, security, deployment, workflow) and the L1–L5 progression that Scaffy reports against.',
  },
  {
    name: 'CIMMI',
    role: 'Dimension sanity check',
    detail:
      'Cross-checks that Scaffy’s dimensions reflect a meaningful capability split and that signals are classified consistently.',
  },
  {
    name: 'Khatami & Zampetti',
    role: 'Concrete YAML rules',
    detail:
      'Source of the workflow smells and positive practices Scaffy detects directly in GitHub Actions and GitLab CI files.',
  },
  {
    name: 'AWS Cloud Adoption',
    role: 'Level wording',
    detail:
      'Used only for the human-readable description of each maturity level. It does not contribute to scoring.',
  },
]

const dimensionCapabilities = [
  {
    dimension: 'Build & Release Management',
    capabilities: [
      'Build scripting maturity',
      'Dependency handling',
      'Packaging & artifacts',
      'Registry / release publish',
      'Versioning / tagging',
    ],
  },
  {
    dimension: 'Testing Maturity',
    capabilities: [
      'Test presence',
      'CI-integrated tests',
      'Reports & coverage',
      'Multi-layer testing',
    ],
  },
  {
    dimension: 'Security Integration',
    capabilities: [
      'Static analysis (SAST)',
      'Dependency / container scanning',
      'Secret hygiene',
      'Safe action / token usage',
      'Policy as code',
    ],
  },
  {
    dimension: 'Deployment Automation',
    capabilities: [
      'Deployment stage presence',
      'Environment targeting',
      'IaC usage',
      'Orchestration maturity',
      'Rollback / controlled rollout',
    ],
  },
  {
    dimension: 'Workflow Quality & Optimization',
    capabilities: [
      'Execution safety',
      'Selective execution',
      'Maintainability',
      'Reproducibility',
      'Matrix / cache optimization',
    ],
  },
]

const capabilityScale = [
  ['0', 'No evidence', 'No matching signal in the YAML for this capability.'],
  ['1', 'Basic signal', 'Minimal presence — one command or one step, no discipline.'],
  ['2', 'Repeatable', 'Structured, repeatable practice with clear jobs and stable steps.'],
  ['3', 'Advanced / governed', 'Advanced or policy-driven configuration.'],
  ['4', 'Mature & modular', 'Highly mature, modular, secure or heavily automated practice.'],
]

const maturityLevels = [
  ['L1', 'Initial / Chaos', 'Almost everything is manual. No reliable build or test cycle.'],
  ['L2', 'Basic CI', 'Build + test + basic artifacts. A pipeline exists but isn’t disciplined.'],
  ['L3', 'Structured Delivery', 'Artifact/release discipline, multi-stage pipeline, versioning.'],
  ['L4', 'Governed Automation', 'Security integration, deployment automation, rollback or policy signals.'],
  ['L5', 'Advanced Pipeline', 'Reusable workflows, policy-as-code, advanced deployment strategies, strong workflow hygiene.'],
]

const promotionCriteria = [
  ['Above L2', 'Build + test pipeline present (build stage detected, no missing-test-stage finding).'],
  ['Above L3', 'Artifact discipline: versioned artifacts or an artifact publish step.'],
  ['Above L4', 'Security Integration > 0 and Deployment Automation > 0 (at least one security scan and one deploy stage).'],
]

const notEvaluatedDimensions = [
  'Collaboration',
  'Telemetry & Observability',
  'Architecture & Design',
  'People & Process',
]

export function Landing() {
  return (
    <AppFrame>
      <section className="landing-hero">
        <HeroShaderCanvas />

        <div className="landing-hero__content">
          <p className="landing-brand">Scaffy</p>
          <h1>Project scaffolding and CI/CD review, built for getting started right.</h1>
          <p>
            Generate a sensible app foundation, then inspect the pipeline that will carry it.
            Scaffy gives new projects structure before the first serious deployment decision.
          </p>
          <div className="landing-actions">
            <Link className="button button--download" to="/init">
              Start a project
            </Link>
            <Link className="button button--secondary" to="/analyze">
              Analyze a pipeline
            </Link>
          </div>
        </div>
      </section>

      <section className="landing-offer" aria-labelledby="initializer-heading">
        <div className="landing-section-heading">
          <Eyebrow>Project initializer</Eyebrow>
          <h2 id="initializer-heading">A first commit that already knows how it should run.</h2>
        </div>
        <div className="landing-copy-block">
          <p>
            The initializer exists for developers who need to start building before they become experts
            in every stack convention, Docker choice, and CI provider detail.
          </p>
          <ol className="landing-number-list">
            {initializerPoints.map((point, index) => (
              <li key={point}>
                <span>{String(index + 1).padStart(2, '0')}</span>
                <p>{point}</p>
              </li>
            ))}
          </ol>
          <Link className="landing-inline-link" to="/init">
            Open initializer
          </Link>
        </div>
      </section>

      <section className="landing-analyzer" aria-labelledby="analyzer-heading">
        <div className="landing-section-heading">
          <Eyebrow>CI/CD analyzer</Eyebrow>
          <h2 id="analyzer-heading">A three-layer model: dimensions, capabilities, YAML rules.</h2>
        </div>
        <div className="landing-analyzer__body">
          <div>
            <p>
              Scaffy reads GitHub Actions and GitLab CI YAML, detects concrete signals, and turns them
              into a maturity report. The model has three layers: five <strong>dimensions</strong> say what
              we measure, <strong>capabilities</strong> describe what higher maturity looks like inside each
              dimension, and explicit <strong>rules</strong> (positive practices and code smells) map YAML
              signals to capability scores.
            </p>
            <div className="signal-strip">
              {analyzerSignals.map((signal) => (
                <Badge key={signal}>{signal}</Badge>
              ))}
            </div>
          </div>
          <div className="model-explainer">
            {modelSources.map((source) => (
              <article key={source.name}>
                <header className="model-explainer__head">
                  <h3>{source.name}</h3>
                  <span>{source.role}</span>
                </header>
                <p>{source.detail}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="landing-methodology" aria-labelledby="methodology-heading">
        <div className="landing-section-heading">
          <Eyebrow>Scoring model</Eyebrow>
          <h2 id="methodology-heading">Capabilities score 0–4. Levels need both points and proof.</h2>
        </div>

        <div className="methodology-body">
          <div className="methodology-formula">
            <p>
              Each dimension is a set of capabilities. A capability earns a score from 0 to 4 based on
              detected positive practices, reduced by code smells. The dimension score is the sum of
              capability points divided by the maximum (4 × capability count). Dimensions with no matching
              signals come back as <em>not evaluated</em> and are excluded from the average — not counted
              as missing.
            </p>
            <code>dimension = sum(capability points) / (4 × capability count)</code>
            <code>overall = average(dimension scores) over evaluated dimensions</code>
          </div>

          <div className="metric-accordion" aria-label="Capabilities by dimension">
            {dimensionCapabilities.map((group) => (
              <details key={group.dimension}>
                <summary>
                  <span>{group.dimension}</span>
                  <strong>{group.capabilities.length} capabilities</strong>
                </summary>
                <div>
                  {group.capabilities.map((capability) => (
                    <p key={capability}>
                      <span>{capability}</span>
                      <strong>0 – 4 pts</strong>
                    </p>
                  ))}
                </div>
              </details>
            ))}
          </div>

          <div className="maturity-scale" aria-label="Capability score scale">
            <h3>Capability score (0 – 4)</h3>
            {capabilityScale.map(([level, label, detail]) => (
              <div key={level}>
                <span>{level}</span>
                <strong>{label}</strong>
                <small>{detail}</small>
              </div>
            ))}
          </div>

          <div className="maturity-scale" aria-label="Pipeline maturity levels">
            <h3>Maturity levels (L1 – L5)</h3>
            {maturityLevels.map(([level, name, detail]) => (
              <div key={level}>
                <span>{level}</span>
                <strong>{name}</strong>
                <small>{detail}</small>
              </div>
            ))}
          </div>

          <div className="promotion-block" aria-label="Promotion criteria between maturity levels">
            <h3>Promotion criteria</h3>
            <p>
              Score alone doesn’t move a project up a level. To clear specific thresholds Scaffy also
              requires concrete signals to be present:
            </p>
            <dl>
              {promotionCriteria.map(([gate, rule]) => (
                <div key={gate}>
                  <dt>{gate}</dt>
                  <dd>{rule}</dd>
                </div>
              ))}
            </dl>
          </div>

          <div className="not-evaluated-block">
            <h3>What Scaffy deliberately doesn’t score</h3>
            <p>
              Some areas can’t be judged honestly from static YAML alone. Scaffy returns them as
              <em> not evaluated</em>, not as missing, so the score isn’t penalised for evidence the
              analyzer can’t see.
            </p>
            <ul>
              {notEvaluatedDimensions.map((name) => (
                <li key={name}>{name}</li>
              ))}
            </ul>
          </div>
        </div>
      </section>

      <section className="landing-flow" aria-labelledby="flow-heading">
        <div>
          <Eyebrow>How it works</Eyebrow>
          <h2 id="flow-heading">One path from blank repository to reviewed delivery pipeline.</h2>
        </div>
        <div className="landing-flow__steps">
          <span>Initialize</span>
          <span>Commit</span>
          <span>Analyze</span>
          <span>Improve</span>
        </div>
      </section>
    </AppFrame>
  )
}

function HeroShaderCanvas() {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return undefined

    const gl = canvas.getContext('webgl', {
      alpha: false,
      antialias: false,
      depth: false,
      stencil: false,
    })
    if (!gl) return undefined
    const canvasElement = canvas
    const glContext = gl

    const vertexShader = createShader(
      glContext,
      glContext.VERTEX_SHADER,
      `
        attribute vec2 a_position;
        void main() {
          gl_Position = vec4(a_position, 0.0, 1.0);
        }
      `,
    )
    const fragmentShader = createShader(
      glContext,
      glContext.FRAGMENT_SHADER,
      `
        precision highp float;

        uniform vec2 u_resolution;
        uniform vec2 u_mouse;
        uniform float u_time;

        float hash(vec2 p) {
          p = fract(p * vec2(123.34, 456.21));
          p += dot(p, p + 45.32);
          return fract(p.x * p.y);
        }

        float noise(vec2 p) {
          vec2 i = floor(p);
          vec2 f = fract(p);
          vec2 u = f * f * (3.0 - 2.0 * f);
          return mix(
            mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
            mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x),
            u.y
          );
        }

        float fbm(vec2 p) {
          float value = 0.0;
          float amplitude = 0.5;
          for (int i = 0; i < 5; i++) {
            value += amplitude * noise(p);
            p = mat2(1.62, 1.18, -1.18, 1.62) * p + 0.17;
            amplitude *= 0.52;
          }
          return value;
        }

        void main() {
          vec2 uv = gl_FragCoord.xy / u_resolution.xy;
          vec2 p = uv * 2.0 - 1.0;
          p.x *= u_resolution.x / u_resolution.y;

          vec2 mouse = u_mouse * 2.0 - 1.0;
          mouse.x *= u_resolution.x / u_resolution.y;

          float t = u_time * 0.11;
          float cursor = exp(-2.9 * distance(p, mouse));
          vec2 drift = vec2(
            fbm(p * 0.82 + vec2(t, -t * 0.45)),
            fbm(p * 0.9 + vec2(-t * 0.38, t * 0.72))
          ) - 0.5;

          vec2 field = p + drift * 0.28 + normalize(mouse - p) * cursor * 0.12;
          vec2 c1 = mouse;
          vec2 c2 = vec2(0.72 + sin(t * 1.7) * 0.22, -0.12 + cos(t * 1.2) * 0.18);
          vec2 c3 = vec2(1.08 + cos(t * 0.9) * 0.18, 0.52 + sin(t * 1.4) * 0.18);

          float glow = exp(-2.6 * distance(field, c1));
          float amber = exp(-2.2 * distance(field, c2));
          float graphiteField = exp(-1.8 * distance(field, c3));
          float flow = fbm(field * 1.55 + vec2(t * 0.7, -t));
          float contour = sin((flow * 4.8 + field.x * 1.2 - field.y * 0.72 - t * 1.8) * 6.28318);
          float rightMask = smoothstep(0.28, 0.82, uv.x);
          float lines = smoothstep(0.975, 1.0, abs(contour)) * rightMask;
          float core = smoothstep(0.18, 0.82, flow);

          vec3 canvas = vec3(0.968, 0.965, 0.94);
          vec3 paper = vec3(1.0, 0.996, 0.972);
          vec3 graphite = vec3(0.23, 0.22, 0.18);
          vec3 ember = vec3(0.96, 0.24, 0.02);
          vec3 gold = vec3(0.95, 0.68, 0.28);
          vec3 bluegrey = vec3(0.56, 0.64, 0.68);

          vec3 color = mix(canvas, paper, core * 0.22);
          color = mix(color, gold, amber * 0.16);
          color = mix(color, ember, glow * 0.14);
          color = mix(color, bluegrey, graphiteField * 0.08);
          color = mix(color, graphite, lines * 0.10);
          color = mix(color, ember, lines * 0.08);

          float textWash = smoothstep(0.72, 0.08, distance(uv, vec2(0.22, 0.46)));
          color = mix(color, paper, textWash * 0.86);
          float bottomWarmth = smoothstep(0.1, 1.0, uv.y) * smoothstep(0.08, 0.88, uv.x);
          color = mix(color, vec3(0.985, 0.91, 0.78), bottomWarmth * 0.05);

          gl_FragColor = vec4(color, 1.0);
        }
      `,
    )
    if (!vertexShader || !fragmentShader) return undefined

    const program = glContext.createProgram()
    if (!program) return undefined

    glContext.attachShader(program, vertexShader)
    glContext.attachShader(program, fragmentShader)
    glContext.linkProgram(program)
    if (!glContext.getProgramParameter(program, glContext.LINK_STATUS)) {
      glContext.deleteProgram(program)
      return undefined
    }

    const buffer = glContext.createBuffer()
    glContext.bindBuffer(glContext.ARRAY_BUFFER, buffer)
    glContext.bufferData(glContext.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), glContext.STATIC_DRAW)

    const position = glContext.getAttribLocation(program, 'a_position')
    const resolution = glContext.getUniformLocation(program, 'u_resolution')
    const mouse = glContext.getUniformLocation(program, 'u_mouse')
    const time = glContext.getUniformLocation(program, 'u_time')

    let frame = 0
    let pointerX = 0.72
    let pointerY = 0.44
    let easedX = pointerX
    let easedY = pointerY
    const startedAt = performance.now()

    function resize() {
      const ratio = Math.min(window.devicePixelRatio || 1, 2)
      const width = Math.floor(canvasElement.clientWidth * ratio)
      const height = Math.floor(canvasElement.clientHeight * ratio)
      if (canvasElement.width !== width || canvasElement.height !== height) {
        canvasElement.width = width
        canvasElement.height = height
        glContext.viewport(0, 0, width, height)
      }
    }

    function movePointer(event: PointerEvent) {
      const bounds = canvasElement.getBoundingClientRect()
      pointerX = (event.clientX - bounds.left) / bounds.width
      pointerY = 1 - (event.clientY - bounds.top) / bounds.height
      pointerX = Math.max(0, Math.min(1, pointerX))
      pointerY = Math.max(0, Math.min(1, pointerY))
    }

    function render(now: number) {
      resize()
      easedX += (pointerX - easedX) * 0.08
      easedY += (pointerY - easedY) * 0.08

      glContext.useProgram(program)
      glContext.bindBuffer(glContext.ARRAY_BUFFER, buffer)
      glContext.enableVertexAttribArray(position)
      glContext.vertexAttribPointer(position, 2, glContext.FLOAT, false, 0, 0)
      glContext.uniform2f(resolution, canvasElement.width, canvasElement.height)
      glContext.uniform2f(mouse, easedX, easedY)
      glContext.uniform1f(time, (now - startedAt) / 1000)
      glContext.drawArrays(glContext.TRIANGLES, 0, 3)

      frame = requestAnimationFrame(render)
    }

    window.addEventListener('pointermove', movePointer)
    window.addEventListener('resize', resize)
    frame = requestAnimationFrame(render)

    return () => {
      cancelAnimationFrame(frame)
      window.removeEventListener('pointermove', movePointer)
      window.removeEventListener('resize', resize)
      glContext.deleteBuffer(buffer)
      glContext.deleteProgram(program)
      glContext.deleteShader(vertexShader)
      glContext.deleteShader(fragmentShader)
    }
  }, [])

  return <canvas aria-hidden="true" className="landing-hero__shader" ref={canvasRef} tabIndex={-1} />
}

function createShader(gl: WebGLRenderingContext, type: number, source: string) {
  const shader = gl.createShader(type)
  if (!shader) return null
  gl.shaderSource(shader, source)
  gl.compileShader(shader)
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    gl.deleteShader(shader)
    return null
  }
  return shader
}
