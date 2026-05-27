import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { AppFrame, Badge, Eyebrow } from '../components'

const initializerPoints = [
  'Choose Angular, Spring Boot, Docker, and a CI provider without memorizing project boilerplate.',
  'Receive a runnable ZIP with project structure, README guidance, and pipeline configuration already wired.',
  'Start with conventions that are useful for students, prototypes, and teams that want a clean first commit.',
]

const analyzerSignals = [
  'Build and release management',
  'Testing maturity and coverage',
  'Security scanning and secret hygiene',
  'Deployment automation and rollback',
  'Workflow quality and reproducibility',
]

const modelSources = [
  {
    name: 'DORA / Accelerate',
    detail: 'Delivery maturity is judged through the same lens as modern DevOps research: fast feedback, repeatable deployment, and recovery-oriented practice.',
  },
  {
    name: 'OWASP CI/CD guidance',
    detail: 'Pipeline security checks look for secrets handling, dependency scanning, SAST signals, and safer automation boundaries.',
  },
  {
    name: 'SLSA supply-chain ideas',
    detail: 'Artifact scoring rewards traceable outputs, reuse, publishing, and evidence that a build result can move safely through a delivery path.',
  },
]

const dimensionCapabilities = [
  {
    dimension: 'Build & release management',
    capabilities: [
      'Build scripting',
      'Packaging',
      'Registry publish',
      'Versioning',
    ],
  },
  {
    dimension: 'Testing maturity',
    capabilities: [
      'Test presence',
      'CI-integrated tests',
      'Reports & coverage',
      'Multi-layer testing',
    ],
  },
  {
    dimension: 'Security integration',
    capabilities: [
      'Static analysis (SAST)',
      'Dependency & container scanning',
      'Secret hygiene',
    ],
  },
  {
    dimension: 'Deployment automation',
    capabilities: [
      'Deployment stage',
      'Environment targeting',
      'IaC usage',
      'Orchestration',
      'Rollback / controlled release',
    ],
  },
  {
    dimension: 'Workflow quality & optimization',
    capabilities: [
      'Execution safety',
      'Selective execution',
      'Maintainability',
      'Reproducibility',
      'Matrix / cache optimization',
      'Lint & static analysis',
      'Formatting',
      'Type checking',
      'Notification channel',
      'Status alerting',
    ],
  },
]

const maturityLevels = [
  ['Level 1', '0%', 'Missing'],
  ['Level 2', '1-39%', 'Early signal'],
  ['Level 3', '40-59%', 'Developing'],
  ['Level 4', '60-79%', 'Strong partial'],
  ['Level 5', '80-100%', 'Complete'],
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
          <h2 id="analyzer-heading">Upload a pipeline and see the practices it proves.</h2>
        </div>
        <div className="landing-analyzer__body">
          <div>
            <p>
              The analyzer reads GitHub Actions and GitLab CI YAML files, detects concrete delivery
              signals, and turns them into a maturity report. It is deterministic: the score comes from
              explicit rules, evidence locations, missing practices, and weighted dimensions.
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
                <h3>{source.name}</h3>
                <p>{source.detail}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="landing-methodology" aria-labelledby="methodology-heading">
        <div className="landing-section-heading">
          <Eyebrow>Scoring model</Eyebrow>
          <h2 id="methodology-heading">Every score is evidence-based, weighted, and explainable.</h2>
        </div>

        <div className="methodology-body">
          <div className="methodology-formula">
            <p>
              Each dimension is composed of capabilities. A capability earns up to 4 points based on
              detected positives, minus any code smells. The dimension score is the total points divided
              by the maximum (4 × capability count). Dimensions without any matching signals are reported
              as <em>not evaluated</em> and excluded from the overall average.
            </p>
            <code>dimension = sum(capability points) / (4 × capability count)</code>
            <code>overall = average(score) over evaluated dimensions</code>
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

          <div className="maturity-scale" aria-label="Scaffy evidence coverage levels">
            <h3>Scaffy evidence coverage levels</h3>
            {maturityLevels.map(([level, range, label]) => (
              <div key={level}>
                <span>{level}</span>
                <strong>{range}</strong>
                <small>{label}</small>
              </div>
            ))}
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
