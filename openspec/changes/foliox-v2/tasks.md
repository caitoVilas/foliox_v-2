# Foliox v2 — Plan de 24 tareas (8 fases)
Fuente: spects.txt. El usuario aprueba tarea por tarea.

## Fase 0 — Cimientos
- [x] 1. Scaffolding Maven multi-module: parent + common + stubs (auth, usuarios, expedientes, documentos, agenda, notificaciones, gateway); Java 25 / Boot 4.1.0 / WebFlux / springdoc 3.1.0
- [x] 2. Docker Compose: PostgreSQL, Kafka 4.3.1 (KRaft, sin ZooKeeper), MinIO
- [x] 3. Módulo common: enums (Fuero, EstadoExpediente, EstadoTarea, Rol), DTOs error, excepciones — dep: 1

## Fase 1 — Auth + Usuarios
- [x] 4. Auth service: Authorization Server propio, login email/password, JWT 2hs — dep: 1,3
- [ ] 5. Creación de estudio + admin ("Crear estudio" del login) — dep: 4
- [ ] 6. Invitaciones por email: link caducado, reenvío, activación — dep: 4,5
- [ ] 7. Usuarios service: CRUD, roles ADMIN/ABOGADO/ASISTENTE, multi-tenant, Resource Server — dep: 4,5

## Fase 2 — Expedientes
- [ ] 8. Expedientes service: crear/consultar con filtros (fuero/estado), borrado solo ADMIN — dep: 7

## Fase 3 — Documentos + Plantillas
- [ ] 9. Documentos: upload PDF a MinIO, CRUD, asociación a expediente, baja solo si expediente CERRADO (o ADMIN) — dep: 2,7,8
- [ ] 10. Plantillas: CRUD global (id, tipo, contenido, versión, activo) + placeholders {{campo}} — dep: 9

## Fase 4 — Agenda + Notificaciones
- [ ] 11. Agenda: crear tareas, filtros (rango/responsable/estado/expediente), cambio estado — dep: 7,8
- [ ] 12. Publicación a Kafka al crear tarea — dep: 2,11
- [ ] 13. Notificaciones: consume Kafka, in-app + email SMTP configurable — dep: 2,12

## Fase 5 — Gateway
- [ ] 14. API Gateway: routing por prefijo, validación JWT, CORS — dep: 4

## Fase 6 — Frontend (React + Vite + TS + Tailwind)
- [ ] 15. Setup front + Login según login.png — dep: 4
- [ ] 16. Layout sidebar/header + Dashboard según ejemplo.png, logo foliox.png — dep: 15
- [ ] 17. CRUD Expedientes — dep: 16,8
- [ ] 18. CRUD Documentos + subida MinIO — dep: 16,9
- [ ] 19. Editor plantillas PDF: canvas con placeholders posicionables — dep: 16,10
- [ ] 20. Agenda: lista/calendario, crear tareas, cambio estado — dep: 16,11
- [ ] 21. Usuarios: lista ADMIN, perfil propio, invitaciones/reenvío — dep: 16,6,7
- [ ] 22. Notificaciones: badge no leídas + centro — dep: 16,13

## Fase 7 — Calidad
- [ ] 23. Tests unitarios por servicio (solo unitarias; Testcontainers fuera de alcance)
- [ ] 24. Documentación .md por servicio + springdoc
