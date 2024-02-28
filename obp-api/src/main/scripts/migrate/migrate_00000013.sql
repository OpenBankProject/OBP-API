UPDATE
  consumer
SET
  perhourcalllimit = -1,
  perdaycalllimit = -1,
  perweekcalllimit = -1,
  permonthcalllimit = -1,
  perminutecalllimit = -1
WHERE
  perhourcalllimit <> -1
  OR perdaycalllimit <> -1
  OR perweekcalllimit <> -1
  OR permonthcalllimit <> -1
  OR perminutecalllimit <> -1;
