#!/usr/bin/env bash

ramls_dir="ramls"

if ! cmd=$(command -v raml-cop); then
  echo "raml-cop is not available. Do 'npm install -g raml-cop'"
  exit 1
fi

script_home="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "${script_home}" || exit

mapfile -t raml_files < <(find ${ramls_dir} -path ${ramls_dir}/raml-util -prune -o -name "*.raml" -print)

if [[ ${#raml_files[@]} -eq 0 ]]; then
  echo "No RAML files found under '${script_home}/${ramls_dir}'"
  exit 1
fi

result=0

#######################################
# Process a file
#
# Do each file separately to assist with error reporting.
# Even though raml-cop can process multiple files, and be a bit faster,
# when there is an issue then this helps to know which file.
#
#######################################
function process_file () {
  local file="$1"
  ${cmd} "${file}"
  if [[ $? -eq 1 ]]; then
    echo "Errors: ${file}"
    result=1
  fi
}

for f in "${raml_files[@]}"; do
  process_file "$f"
done

if [[ "${result}" -eq 1 ]]; then
  echo "Some assistance is at https://dev.folio.org/guides/raml-cop"
fi

exit ${result}
