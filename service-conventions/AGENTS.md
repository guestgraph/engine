<!-- service-conventions · v0.3.0 -->
The code-level rules of every guestgraph service on the Spring stack live in
`service-conventions/`, vendored from guestgraph/service-conventions at the release
`service-conventions.json` names: the parent build every `pom.xml` takes by path, the source rules,
the architecture rules in `src/test/java/ServiceRulesTest.java`, the diagram script, the workflow
in `.github/workflows/verify.yml`, and this block. `sh service-conventions/service-conventions-sync
check` says whether the copy matches the release, `sync` brings it to the release the pin names,
and `sh service-conventions/service-conventions-check` says what of the list the service lacks.
What every service has, whatever its stack, is `SERVICE.md` there. Edit a shared file in
guestgraph/service-conventions, never here.
<!-- end service-conventions -->
