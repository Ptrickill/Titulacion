-- =====================================================================
-- Rehab Elite - V2: initial ICD-10 catalog
-- WARNING: TEST SUBSET of codes frequently used in physiotherapy.
-- Codes and descriptions must be validated against the official ICD-10
-- publication (PAHO/WHO Spanish edition, adopted by Ecuador's MSP)
-- before production use. Corrections go into a new migration.
-- Descriptions are user-facing and therefore stay in Spanish.
-- =====================================================================

INSERT INTO icd10_diagnoses (code, description) VALUES
    -- Spine
    ('M54.2', 'Cervicalgia'),
    ('M54.4', 'Lumbago con ciática'),
    ('M54.5', 'Lumbago no especificado'),
    ('M51.1', 'Trastorno de disco lumbar y otros, con radiculopatía'),
    ('M50.1', 'Trastorno de disco cervical con radiculopatía'),
    ('M47.8', 'Otras espondilosis'),
    ('M41.9', 'Escoliosis, no especificada'),
    ('M53.1', 'Síndrome cervicobraquial'),
    -- Shoulder and upper limb
    ('M75.0', 'Capsulitis adhesiva del hombro'),
    ('M75.1', 'Síndrome del manguito rotatorio'),
    ('M75.4', 'Síndrome de abducción dolorosa del hombro'),
    ('M75.5', 'Bursitis del hombro'),
    ('M77.1', 'Epicondilitis lateral'),
    ('G56.0', 'Síndrome del túnel carpiano'),
    -- Knee
    ('M17.9', 'Gonartrosis, no especificada'),
    ('M22.2', 'Trastornos rotulianofemorales'),
    ('M23.2', 'Trastorno de menisco debido a desgarro o lesión antigua'),
    ('S83.2', 'Desgarro de meniscos, presente'),
    ('S83.5', 'Esguince y torcedura que compromete el ligamento cruzado (anterior) (posterior) de la rodilla'),
    -- Ankle and foot
    ('S93.4', 'Esguince y torcedura del tobillo'),
    ('M76.6', 'Tendinitis aquiliana'),
    ('M72.2', 'Fibromatosis de la aponeurosis plantar'),
    -- Fractures
    ('S42.2', 'Fractura de la epífisis superior del húmero'),
    ('S52.5', 'Fractura de la epífisis inferior del radio'),
    ('S72.0', 'Fractura del cuello del fémur'),
    ('S82.6', 'Fractura del maléolo externo'),
    -- Neurological
    ('G20',   'Enfermedad de Parkinson'),
    ('G35',   'Esclerosis múltiple'),
    ('I69.3', 'Secuelas de infarto cerebral'),
    ('I69.4', 'Secuelas de accidente vascular encefálico, no especificado como hemorrágico o por isquemia'),
    -- Post-surgical states and other
    ('Z96.6', 'Presencia de implantes articulares ortopédicos'),
    ('Z98.8', 'Otros estados postquirúrgicos especificados'),
    ('Z47.8', 'Otros cuidados especificados de control ortopédico'),
    ('M79.1', 'Mialgia');
