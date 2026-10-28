import asyncio
from playwright.async_api import async_playwright

W=2300
boxes=[]  # (x,y,w,h,stereo,title,tech,desc,style)
def box(x,y,w,h,stereo,title,tech,desc,style="comp"):
    boxes.append((x,y,w,h,stereo,title,tech,desc,style))
    return dict(x=x,y=y,w=w,h=h,cx=x+w/2,cy=y+h/2,b=y+h,r=x+w)

spa=box(980,30,340,150,"container","Aplicación web SPA","Angular 22","Envía peticiones REST con token JWT.","cont")
fil=box(960,330,380,170,"component","Filtro de seguridad","Spring Security, JWT","Valida el token, resuelve el rol y bloquea el acceso no autorizado (HTTP 403).")
mods=[("Módulo Autenticación y Usuarios","auth · Controller, Service, Repository","Inicio de sesión, bloqueo por intentos fallidos, alta y estado de cuentas por rol."),
("Módulo Pacientes","patients · Controller, Service, Repository","Registro único por cédula, número de historia clínica y datos demográficos."),
("Módulo Historial Clínico","clinicalrecords · Controller, Service, Repository","Antecedentes, tratamientos, diagnóstico CIE-10, plan, responsable y estado."),
("Módulo Sesiones y Línea de Tiempo","sessions · Controller, Service, Repository","Sesiones con EVA, correlativo, versiones y consulta cronológica."),
("Módulo Resumen IA","aisummary · Controller, Service, Pseudonymizer, AI client","Prepara el texto sin datos identificativos y gestiona validación, edición o descarte."),
("Módulo Portal del Paciente","portal · Controller, Service","Consulta de solo lectura del progreso, sesiones e indicaciones propias."),
("Módulo Reportes","reports · Controller, Service, JPQL","Indicadores de tratamientos y sesiones con filtros y exportación PDF.")]
mw,gap,mx0,my=222,42,90,650
M=[box(mx0+i*(mw+gap),my,mw,290,"component",t,te,d) for i,(t,te,d) in enumerate(mods)]
per=box(780,1120,380,190,"component","Capa de persistencia","Spring Data JPA, Hibernate, Flyway","Repositorios por módulo, transacciones y migraciones versionadas del esquema.")
aud=box(1380,1120,380,190,"component","Módulo Auditoría","audit · Event listener, Hibernate Envers","Registra usuario, acción, entidad y fecha de cada operación clínica.")
db=box(800,1460,340,170,"container","PostgreSQL 16","Render PostgreSQL","Esquema relacional y tablas de auditoría.","db")
ia=box(1990,1120,280,170,"external_system","API de Claude (Anthropic)","HTTPS / JSON","Modelo de lenguaje que genera la síntesis.","ext")
# boundary
bx,by,bw,bh=40,240,1880,1130

lines=[]; labels=[]
def arrow(pts,label=None,lx=None,ly=None,tech=None):
    lines.append(pts)
    if label: labels.append((lx,ly,label,tech))
arrow([(spa['cx'],spa['b']),(fil['cx'],fil['y'])],"Peticiones REST",spa['cx']+12,250,"JSON / HTTPS")
busy=590
arrow([(fil['cx'],fil['b']),(fil['cx'],busy)],"Delega la petición autorizada",fil['cx']+12,530,None)
lines.append([(M[0]['cx'],busy),(M[-1]['cx'],busy)])
for m in M: arrow([(m['cx'],busy),(m['cx'],m['y'])])
# inter-module (horizontal, right to left)
def h(a,b,label,dy):
    y=a['y']+dy
    arrow([(a['x'],y),(b['r'],y)],label,(a['x']+b['r'])/2,y-12,None)
h(M[2],M[1],"usa",150); h(M[3],M[2],"usa",150); h(M[4],M[3],"usa",150)
# IA external
yy=1000
arrow([(M[4]['r']-40,M[4]['b']),(M[4]['r']-40,yy),(ia['cx'],yy),(ia['cx'],ia['y'])],"Solicita síntesis",ia['cx']+10,1045,"HTTPS / JSON")
# modules to persistence bus
bus2=1050
lines.append([(M[0]['cx'],bus2),(M[-1]['cx'],bus2)])
for m in M: lines.append([(m['cx'],m['b']),(m['cx'],bus2)])
arrow([(per['cx'],bus2),(per['cx'],per['y'])],"Usa repositorios",per['cx']+12,1090,None)
arrow([(aud['x'],aud['cy']),(per['r'],per['cy'])],"Registra eventos",(aud['x']+per['r'])/2,aud['cy']-12,None)
arrow([(per['cx'],per['b']),(db['cx'],db['y'])],"Lee y escribe",per['cx']+12,1405,"JDBC / TLS")

def esc(s): return s.replace('&','&amp;').replace('<','&lt;')
html=f'''<html><head><meta charset="utf-8"><style>
body{{margin:0;font-family:"DejaVu Sans",Arial;background:#fff}}
.b{{position:absolute;box-sizing:border-box;border:2px solid #555;background:#F7F7F7;text-align:center;padding:10px 12px;display:flex;flex-direction:column;justify-content:center}}
.cont{{background:#EDEDED;border-color:#444}} .ext{{background:#fff;border:2px dashed #555}}
.db{{background:#EDEDED;border-radius:50%/22px;border-color:#444}}
.st{{font-style:italic;font-size:15px;color:#333}} .t{{font-weight:bold;font-size:21px;margin:2px 0}}
.te{{font-style:italic;font-size:15px}} .d{{font-size:17px;margin-top:8px;line-height:1.25}}
.lab{{position:absolute;font-size:15px;font-weight:bold;color:#222;background:#fff;padding:0 3px;white-space:nowrap}}
.lab i{{font-weight:normal}}
.bd{{position:absolute;border:2px dashed #555;box-sizing:border-box}}
.bt{{position:absolute;text-align:center;font-size:20px;font-weight:bold}}
</style></head><body>
<div class="bd" style="left:{bx}px;top:{by}px;width:{bw}px;height:{bh}px"></div>
<div class="bt" style="left:{bx+20}px;top:{by+8}px;text-align:left"><span class="st" style="font-weight:normal">«boundary»</span><br>API REST monolítica modular (Spring Boot 4)<br><span style="font-size:15px">[Container]</span></div>
<svg width="{W}" height="1680" style="position:absolute;left:0;top:0">
<defs><marker id="a" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto"><path d="M0,0 L12,6 L0,12 L3,6 z" fill="#333"/></marker></defs>
'''
for pts in lines:
    d="M"+" L".join(f"{x},{y}" for x,y in pts)
    end = pts[-1]
    is_bus = (len(pts)==2 and pts[0][1]==pts[1][1] and pts[0][1] in (busy,bus2)) or (pts[-1][1]==bus2)
    mk = '' if is_bus else 'marker-end="url(#a)"'
    html+=f'<path d="{d}" stroke="#333" stroke-width="2" fill="none" {mk}/>\n'
html+='</svg>\n'
for (x,y,w,hh,st,t,te,d,sty) in boxes:
    html+=f'<div class="b {sty if sty!="comp" else ""}" style="left:{x}px;top:{y}px;width:{w}px;height:{hh}px"><div class="st">«{st}»</div><div class="t">{esc(t)}</div><div class="te">[{esc(te)}]</div><div class="d">{esc(d)}</div></div>\n'
for (x,y,l,t) in labels:
    tt=f'<br><i>[{t}]</i>' if t else ''
    html+=f'<div class="lab" style="left:{x}px;top:{y-12}px;transform:translateX({"-50%" if x<9999 and l in ("usa","Registra eventos") else "0"})">{esc(l)}{tt}</div>\n'
html+='</body></html>'
open('/home/claude/diagramas/fig14.html','w').write(html)
async def main():
    async with async_playwright() as p:
        b=await p.chromium.launch(executable_path='/opt/pw-browsers/chromium-1243/chrome-linux64/chrome')
        pg=await b.new_page(viewport={'width':W,'height':1660},device_scale_factor=1.5)
        await pg.goto('file:///home/claude/diagramas/fig14.html')
        await pg.screenshot(path='/home/claude/diagramas/fig14_c4_n3.png',full_page=True)
        await b.close()
asyncio.run(main())
