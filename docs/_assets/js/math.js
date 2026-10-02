// Math fences preserve TeX through Scaladoc's Markdown parser.
window.MathJax = {
  startup: {
    typeset: false,
    pageReady: async () => {
      for (const code of document.querySelectorAll("code.language-math")) {
        const math = await MathJax.tex2chtmlPromise(code.textContent, {
          display: true,
        });
        (code.closest(".snippet") || code.parentElement).replaceWith(math);
      }
      // GitHub's $`...`$ inline syntax survives Markdown as $<code>...</code>$.
      for (const code of document.querySelectorAll("p code, li code")) {
        const before = code.previousSibling;
        const after = code.nextSibling;
        if (before?.nodeType === 3 && after?.nodeType === 3 &&
            before.textContent.endsWith("$") && after.textContent.startsWith("$")) {
          const math = await MathJax.tex2chtmlPromise(code.textContent, {
            display: false,
          });
          before.textContent = before.textContent.slice(0, -1);
          after.textContent = after.textContent.slice(1);
          code.replaceWith(math);
        }
      }
    },
  },
};

const script = document.createElement("script");
script.src = "https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-chtml.js";
script.async = true;
document.head.appendChild(script);
